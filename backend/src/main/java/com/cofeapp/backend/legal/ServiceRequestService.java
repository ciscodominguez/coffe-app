package com.cofeapp.backend.legal;

import com.cofeapp.backend.billing.BillingService;
import com.cofeapp.backend.business.BusinessRepository;
import com.cofeapp.backend.common.exception.NotFoundException;
import com.cofeapp.backend.common.exception.TooManyRequestsException;
import com.cofeapp.backend.common.mail.MailService;
import com.cofeapp.backend.domain.Business;
import com.cofeapp.backend.domain.ServiceRequest;
import com.cofeapp.backend.legal.dto.CreateServiceRequest;
import com.cofeapp.backend.legal.dto.CreatedServiceRequestResponse;
import com.cofeapp.backend.legal.dto.ServiceRequestResponse;
import com.cofeapp.backend.legal.dto.UpdateServiceRequest;
import com.cofeapp.backend.mercadopago.client.MercadoPagoApiException;
import com.cofeapp.backend.platform.PlatformProperties;
import jakarta.persistence.EntityManager;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Botón de arrepentimiento" y "Botón de baja" (V17 — Defensa del
 * Consumidor). Formulario público: se contesta al instante con un código de
 * trámite (y se le manda por mail), y le llega a Fran a su panel (y por
 * mail, si está configurado el correo).
 *
 * <p>Si el CUIT y el email coinciden con los del dueño de un café, se corta
 * solo el débito automático de la suscripción (así no se le cobra más
 * mientras Fran lo atiende). Lo que ya pagó lo sigue usando hasta el final.
 * El reintegro de un arrepentimiento lo hace Fran a mano desde Mercado Pago.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceRequestService {

  static final String WITHDRAWAL = "WITHDRAWAL";
  static final int MAX_PER_HOUR = 5;
  private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final SecureRandom RANDOM = new SecureRandom();

  private final ServiceRequestRepository repository;
  private final BusinessRepository businessRepository;
  private final BillingService billingService;
  private final MailService mailService;
  private final PlatformProperties platformProperties;
  private final EntityManager em;

  /** Freno contra abusos: hasta 5 pedidos por hora desde la misma IP. */
  private final Map<String, Deque<Instant>> recentByIp = new ConcurrentHashMap<>();

  @Transactional
  public CreatedServiceRequestResponse create(CreateServiceRequest input) {
    String ip = ClientInfo.ip();
    throttle(ip == null ? "?" : ip);
    boolean withdrawal = WITHDRAWAL.equals(input.type());

    ServiceRequest request = new ServiceRequest();
    request.setCode(newCode(withdrawal ? "ARR" : "BAJ"));
    request.setType(input.type());
    request.setContactName(input.contactName().trim());
    request.setEmail(input.email().trim().toLowerCase());
    request.setTaxId(blankToNull(input.taxId()));
    request.setCafeName(blankToNull(input.cafeName()));
    request.setMessage(blankToNull(input.message()));
    request.setStatus("PENDING");
    request.setIp(ip);

    Business business = match(request.getTaxId(), request.getEmail());
    if (business != null) {
      request.setBusinessId(business.getId());
      try {
        request.setResult(
            billingService.cancelDebitIfAny(business)
                ? "Débito automático cancelado solo al recibir el pedido"
                : "El café no tenía débito automático activo");
      } catch (MercadoPagoApiException ex) {
        log.warn("No se pudo cancelar el débito del café {}: {}", business.getId(), ex.getMessage());
        request.setResult("No se pudo cancelar el débito en Mercado Pago: hacelo a mano");
      }
    } else {
      request.setResult("El CUIT y el email no coinciden con ningún café: revisalo a mano");
    }
    repository.save(request);
    notifyByMail(request, withdrawal);
    return new CreatedServiceRequestResponse(request.getCode(), request.getType(), request.getCreatedAt());
  }

  @Transactional(readOnly = true)
  public List<ServiceRequestResponse> findAll() {
    Map<String, String> names = new HashMap<>();
    businessRepository.findAll()
        .forEach(b -> names.put(b.getId(), b.getTradeName() != null && !b.getTradeName().isBlank() ? b.getTradeName() : b.getLegalName()));
    return repository.findTop200ByOrderByCreatedAtDesc().stream().map(r -> toResponse(r, names)).toList();
  }

  public long pendingCount() {
    return repository.countByStatus("PENDING");
  }

  @Transactional
  public ServiceRequestResponse update(String id, UpdateServiceRequest input) {
    ServiceRequest request = repository.findById(id).orElseThrow(() -> new NotFoundException("No se encontró el pedido."));
    if (input.status() != null && !input.status().equals(request.getStatus())) {
      request.setStatus(input.status());
      request.setHandledAt("DONE".equals(input.status()) ? Instant.now() : null);
    }
    if (input.adminNote() != null) request.setAdminNote(blankToNull(input.adminNote()));
    repository.save(request);
    Map<String, String> names = new HashMap<>();
    if (request.getBusinessId() != null) {
      businessRepository.findById(request.getBusinessId()).ifPresent(b -> names.put(b.getId(), b.getTradeName() != null ? b.getTradeName() : b.getLegalName()));
    }
    return toResponse(request, names);
  }

  // ---------------------------------------------------------------------

  /** El café cuyo dueño tiene ese email y ese CUIT (las dos cosas tienen que coincidir). */
  private Business match(String taxId, String email) {
    if (taxId == null) return null;
    String digits = taxId.replaceAll("\\D", "");
    if (digits.length() != 11) return null;
    List<String> ids =
        em.createQuery(
                "select b.id from Business b, User u where u.businessId = b.id"
                    + " and u.role = com.cofeapp.backend.domain.enums.UserRole.OWNER"
                    + " and lower(u.email) = :email and replace(b.taxId, '-', '') = :digits",
                String.class)
            .setParameter("email", email)
            .setParameter("digits", digits)
            .getResultList();
    return ids.isEmpty() ? null : businessRepository.findById(ids.get(0)).orElse(null);
  }

  private void notifyByMail(ServiceRequest request, boolean withdrawal) {
    String what = withdrawal ? "arrepentimiento" : "baja";
    mailService.send(
        request.getEmail(),
        "COFFE_APP — recibimos tu pedido de " + what + " (" + request.getCode() + ")",
        "Hola " + request.getContactName() + ":\n\n"
            + "Recibimos tu pedido de " + what + " del servicio COFFE_APP.\n"
            + "Tu código de trámite es: " + request.getCode() + "\n\n"
            + (request.getBusinessId() != null
                ? "Ya cancelamos el débito automático de la suscripción, si lo tenías. "
                : "")
            + "Te vamos a escribir a este email para confirmarlo.\n\nCOFFE_APP");
    String admin = platformProperties.adminEmail();
    if (admin != null && !admin.isBlank()) {
      mailService.send(
          admin.trim(),
          "Nuevo pedido de " + what + " — " + request.getCode(),
          "Nombre: " + request.getContactName() + "\nEmail: " + request.getEmail() + "\nCUIT: " + nullToDash(request.getTaxId())
              + "\nCafé: " + nullToDash(request.getCafeName()) + "\nMensaje: " + nullToDash(request.getMessage())
              + "\n\n" + nullToDash(request.getResult()) + "\n\nLo ves en el panel de superadmin → Pedidos de baja.");
    }
  }

  private void throttle(String ip) {
    Instant now = Instant.now();
    Deque<Instant> times = recentByIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
    synchronized (times) {
      while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(Duration.ofHours(1)))) times.pollFirst();
      if (times.size() >= MAX_PER_HOUR) {
        throw new TooManyRequestsException("Recibimos muchos pedidos seguidos desde esta conexión. Probá de nuevo en un rato.");
      }
      times.addLast(now);
    }
  }

  private String newCode(String prefix) {
    for (int attempt = 0; attempt < 10; attempt++) {
      StringBuilder code = new StringBuilder(prefix).append('-');
      for (int i = 0; i < 6; i++) code.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
      if (!repository.existsByCode(code.toString())) return code.toString();
    }
    throw new IllegalStateException("No se pudo generar un código de trámite.");
  }

  private static ServiceRequestResponse toResponse(ServiceRequest r, Map<String, String> names) {
    return new ServiceRequestResponse(
        r.getId(), r.getCode(), r.getType(), r.getBusinessId(), r.getBusinessId() == null ? null : names.get(r.getBusinessId()),
        r.getContactName(), r.getEmail(), r.getTaxId(), r.getCafeName(), r.getMessage(), r.getStatus(), r.getResult(),
        r.getAdminNote(), r.getCreatedAt(), r.getHandledAt());
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String nullToDash(String value) {
    return value == null ? "—" : value;
  }
}
