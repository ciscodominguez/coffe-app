package com.cofeapp.backend.support;

import com.cofeapp.backend.billing.BillingService;
import com.cofeapp.backend.business.BusinessRepository;
import com.cofeapp.backend.common.context.TenantContext;
import com.cofeapp.backend.common.exception.NotFoundException;
import com.cofeapp.backend.common.exception.TooManyRequestsException;
import com.cofeapp.backend.common.mail.MailService;
import com.cofeapp.backend.domain.Business;
import com.cofeapp.backend.domain.SupportRequest;
import com.cofeapp.backend.domain.User;
import com.cofeapp.backend.platform.PlatformProperties;
import com.cofeapp.backend.support.dto.CreateSupportRequest;
import com.cofeapp.backend.support.dto.SupportInfoResponse;
import com.cofeapp.backend.support.dto.SupportRequestResponse;
import com.cofeapp.backend.support.dto.UpdateSupportRequest;
import com.cofeapp.backend.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Botón de soporte (V18). Quien está en un café y se le traba el sistema
 * le escribe a Fran por WhatsApp (el mensaje lo arma el frontend) y además
 * deja acá un pedido de ayuda con los datos técnicos, que Fran ve en su
 * panel (y le llega por mail si está configurado el correo).
 */
@Service
@RequiredArgsConstructor
public class SupportService {

  /** Freno contra el botón apretado muchas veces: hasta 10 por hora por persona. */
  static final int MAX_PER_HOUR = 10;

  private final SupportRequestRepository repository;
  private final BusinessRepository businessRepository;
  private final UserRepository userRepository;
  private final TenantContext tenantContext;
  private final BillingService billingService;
  private final PlatformProperties platformProperties;
  private final MailService mailService;

  public SupportInfoResponse info() {
    String contact = platformProperties.supportContact();
    return new SupportInfoResponse(
        billingService.settings().getSupportWhatsapp(), contact == null || contact.isBlank() ? null : contact.trim());
  }

  @Transactional
  public SupportRequestResponse create(CreateSupportRequest input) {
    String userId = tenantContext.getUserId();
    if (repository.countByUserIdAndCreatedAtAfter(userId, Instant.now().minus(Duration.ofHours(1))) >= MAX_PER_HOUR) {
      throw new TooManyRequestsException("Ya mandaste varios pedidos de ayuda seguidos. Fran ya está avisado.");
    }
    User user = userRepository.findByIdScoped(userId).orElse(null);
    Business business =
        businessRepository.findById(tenantContext.getBusinessId()).orElseThrow(() -> new NotFoundException("No se encontró el café."));

    SupportRequest request = new SupportRequest();
    request.setBusinessId(business.getId());
    request.setUserId(userId);
    request.setUserName(user == null ? tenantContext.getEmail() : (user.getFirstName() + " " + user.getLastName()).trim());
    request.setUserRole(tenantContext.getRole());
    request.setPage(trim(input.page(), 255));
    request.setMessage(trim(input.message(), 1000));
    request.setLastError(trim(input.lastError(), 1000));
    request.setUserAgent(trim(input.userAgent(), 255));
    request.setStatus("PENDING");
    repository.save(request);

    String admin = platformProperties.adminEmail();
    if (admin != null && !admin.isBlank()) {
      mailService.send(
          admin.trim(),
          "Pedido de ayuda — " + name(business),
          "Café: " + name(business) + "\nQuién: " + request.getUserName() + " (" + request.getUserRole() + ")"
              + "\nPantalla: " + dash(request.getPage()) + "\nQué pasó: " + dash(request.getMessage())
              + "\nÚltimo error: " + dash(request.getLastError()) + "\nNavegador: " + dash(request.getUserAgent())
              + "\n\nLo ves en el panel de superadmin → Pedidos de ayuda.");
    }
    return toResponse(request, Map.of(business.getId(), name(business)));
  }

  @Transactional(readOnly = true)
  public List<SupportRequestResponse> findAll() {
    Map<String, String> names = new HashMap<>();
    businessRepository.findAll().forEach(b -> names.put(b.getId(), name(b)));
    return repository.findTop200ByOrderByCreatedAtDesc().stream().map(r -> toResponse(r, names)).toList();
  }

  @Transactional
  public SupportRequestResponse update(String id, UpdateSupportRequest input) {
    SupportRequest request = repository.findById(id).orElseThrow(() -> new NotFoundException("No se encontró el pedido de ayuda."));
    if (input.status() != null && !input.status().equals(request.getStatus())) {
      request.setStatus(input.status());
      request.setHandledAt("DONE".equals(input.status()) ? Instant.now() : null);
    }
    if (input.adminNote() != null) request.setAdminNote(input.adminNote().isBlank() ? null : input.adminNote().trim());
    repository.save(request);
    Map<String, String> names = new HashMap<>();
    businessRepository.findById(request.getBusinessId()).ifPresent(b -> names.put(b.getId(), name(b)));
    return toResponse(request, names);
  }

  // ---------------------------------------------------------------------

  private static SupportRequestResponse toResponse(SupportRequest r, Map<String, String> names) {
    return new SupportRequestResponse(
        r.getId(), r.getBusinessId(), names.get(r.getBusinessId()), r.getUserName(), r.getUserRole(), r.getPage(), r.getMessage(),
        r.getLastError(), r.getUserAgent(), r.getStatus(), r.getAdminNote(), r.getCreatedAt(), r.getHandledAt());
  }

  private static String name(Business b) {
    return b.getTradeName() != null && !b.getTradeName().isBlank() ? b.getTradeName() : b.getLegalName();
  }

  private static String trim(String value, int max) {
    if (value == null || value.isBlank()) return null;
    String v = value.trim();
    return v.length() <= max ? v : v.substring(0, max);
  }

  private static String dash(String value) {
    return value == null ? "—" : value;
  }
}
