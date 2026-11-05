package com.cofeapp.backend.billing;

import com.cofeapp.backend.billing.dto.BillingStatusResponse;
import com.cofeapp.backend.billing.dto.CheckoutResponse;
import com.cofeapp.backend.billing.dto.PlatformSettingsDto;
import com.cofeapp.backend.business.BusinessRepository;
import com.cofeapp.backend.common.exception.ConflictException;
import com.cofeapp.backend.common.exception.NotFoundException;
import com.cofeapp.backend.domain.Business;
import com.cofeapp.backend.domain.PlatformAction;
import com.cofeapp.backend.domain.PlatformSettings;
import com.cofeapp.backend.domain.SubscriptionPayment;
import com.cofeapp.backend.domain.enums.BusinessPlan;
import com.cofeapp.backend.domain.enums.BusinessStatus;
import com.cofeapp.backend.mercadopago.client.MercadoPagoApiException;
import com.cofeapp.backend.mercadopago.client.MercadoPagoClient;
import com.cofeapp.backend.mercadopago.client.MercadoPagoClient.MpAuthorizedPayment;
import com.cofeapp.backend.mercadopago.client.MercadoPagoClient.MpPreapproval;
import com.cofeapp.backend.platform.PlatformActionRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cobro de la suscripción de COFFE_APP (V16 — ver README, paso 3.2).
 *
 * <p>Cómo funciona:
 *
 * <ol>
 *   <li>Un café nuevo arranca con {@code trialDays} (14) de prueba gratis.
 *   <li>El dueño toca "Suscribirme": se crea una suscripción de Mercado
 *       Pago (débito automático mensual, a la cuenta de Fran) que empieza
 *       el día que termina la prueba, y el dueño la confirma en la página
 *       de Mercado Pago con su tarjeta.
 *   <li>Cada débito aprobado deja el café pago hasta un mes después.
 *   <li>Si se vence (no se suscribió, o le rechazan la tarjeta), tiene
 *       {@code graceDays} (7) con un aviso; después se suspende solo. El
 *       dueño puede seguir entrando a pagar; al pagar, se reactiva solo.
 * </ol>
 *
 * <p>Lo que dice Mercado Pago se consulta SIEMPRE a su API con el token de
 * Fran (los avisos solo disparan la consulta), así un aviso falso no puede
 * marcar nada como pago.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

  static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
  static final String PAYMENT_REASON = "Falta de pago de la suscripción";
  private static final Set<String> LIVE_SUBSCRIPTION = Set.of("pending", "authorized", "paused");

  private final BusinessRepository businessRepository;
  private final PlatformSettingsRepository settingsRepository;
  private final SubscriptionPaymentRepository paymentRepository;
  private final PlatformActionRepository actionRepository;
  private final MercadoPagoClient mercadoPago;
  private final BillingProperties properties;

  @Value("${app.frontend-url:http://localhost:4200}")
  private String frontendUrl;

  // ---------------------------------------------------------------------
  // Configuración (panel de Fran)
  // ---------------------------------------------------------------------

  @Transactional
  public PlatformSettings settings() {
    return settingsRepository.findAll().stream()
        .findFirst()
        .orElseGet(() -> settingsRepository.save(new PlatformSettings()));
  }

  @Transactional(readOnly = true)
  public PlatformSettingsDto settingsDto() {
    PlatformSettings settings = settings();
    return new PlatformSettingsDto(
        settings.getMonthlyPrice(),
        settings.getTrialDays(),
        settings.getGraceDays(),
        properties.hasMercadoPago(),
        settings.getSupportWhatsapp());
  }

  @Transactional
  public PlatformSettingsDto updateSettings(PlatformSettingsDto request) {
    PlatformSettings settings = settings();
    settings.setMonthlyPrice(request.monthlyPrice());
    settings.setTrialDays(request.trialDays());
    settings.setGraceDays(request.graceDays());
    settings.setSupportWhatsapp(
        request.supportWhatsapp() == null || request.supportWhatsapp().isBlank() ? null : request.supportWhatsapp().trim());
    settingsRepository.save(settings);
    return settingsDto();
  }

  /** Fin de la prueba de un café que se está registrando ahora. */
  public Instant newTrialEnd() {
    return Instant.now().plus(Duration.ofDays(settings().getTrialDays()));
  }

  public int graceDays() {
    return settings().getGraceDays();
  }

  // ---------------------------------------------------------------------
  // Estado (pantalla Suscripción del café y panel de Fran)
  // ---------------------------------------------------------------------

  @Transactional(readOnly = true)
  public BillingStatusResponse status(String businessId) {
    Business business = find(businessId);
    PlatformSettings settings = settings();
    BillingState state = BillingState.of(business, settings.getGraceDays(), Instant.now());

    String unavailable = null;
    if ("EXEMPT".equals(state.state())) unavailable = "Este café no paga suscripción.";
    // Suscripto pero vencido (le rechazan la tarjeta): puede suscribirse de
    // nuevo con otra — se cancela la anterior (ver checkout).
    else if ("authorized".equals(business.getMpPreapprovalStatus()) && !isOverdue(state)) unavailable = "Ya estás suscripto.";
    else if (settings.getMonthlyPrice() == null) unavailable = "Todavía no está disponible la suscripción. Te avisamos cuando lo esté.";
    else if (!properties.hasMercadoPago()) unavailable = "Todavía no está disponible el pago con Mercado Pago. Te avisamos cuando lo esté.";

    List<BillingStatusResponse.Payment> payments =
        paymentRepository.findTop24ByBusinessIdOrderByChargedAtDesc(businessId).stream()
            .map(p -> new BillingStatusResponse.Payment(p.getChargedAt(), p.getAmount(), p.getStatus(), p.getStatusDetail(), p.getCoversUntil()))
            .toList();

    return new BillingStatusResponse(
        state.state(),
        business.getTrialEndsAt(),
        business.getPaidUntil(),
        state.accessUntil(),
        state.suspendsAt(),
        state.daysLeft(),
        settings.getMonthlyPrice(),
        settings.getGraceDays(),
        business.getMpPreapprovalStatus(),
        business.getMpPayerEmail(),
        unavailable == null,
        unavailable,
        Boolean.TRUE.equals(business.getBillingExempt()),
        payments);
  }

  // ---------------------------------------------------------------------
  // Suscribirse / cancelar (dueño)
  // ---------------------------------------------------------------------

  /**
   * Crea la suscripción en Mercado Pago y devuelve la página donde el
   * dueño la confirma con su tarjeta. El primer débito es el día que se le
   * termina lo que ya tiene (la prueba o lo pago), o ya si está vencido.
   */
  @Transactional
  public CheckoutResponse checkout(String businessId, String payerEmail) {
    BillingStatusResponse current = status(businessId);
    if (!current.canSubscribe()) throw new ConflictException(current.unavailableReason());
    Business business = find(businessId);

    // Tenía débito automático pero está vencido (tarjeta rechazada): se
    // cancela el anterior para que no se cobre dos veces, y se arranca uno
    // nuevo que debita ya.
    if ("authorized".equals(business.getMpPreapprovalStatus()) || "paused".equals(business.getMpPreapprovalStatus())) {
      mercadoPago.cancelPreapproval(properties.mpAccessToken(), business.getMpPreapprovalId());
      business.setMpPreapprovalStatus("cancelled");
    }

    Instant soon = Instant.now().plus(Duration.ofMinutes(10));
    Instant start = current.accessUntil() != null && current.accessUntil().isAfter(soon) ? current.accessUntil() : soon;

    MpPreapproval created =
        mercadoPago.createPreapproval(
            properties.mpAccessToken(),
            new MercadoPagoClient.MpPreapprovalRequest(
                "COFFE_APP — " + displayName(business),
                business.getId(),
                payerEmail.trim().toLowerCase(),
                current.monthlyPrice(),
                "ARS",
                start,
                frontendUrl.replaceAll("/+$", "") + "/suscripcion?volviste=1"));
    if (created.initPoint() == null) {
      throw new ConflictException("Mercado Pago no devolvió la página para suscribirse. Probá de nuevo en un rato.");
    }

    business.setMpPreapprovalId(created.id());
    business.setMpPreapprovalStatus(created.status() == null ? "pending" : created.status());
    business.setMpPayerEmail(payerEmail.trim().toLowerCase());
    businessRepository.save(business);
    return new CheckoutResponse(created.initPoint());
  }

  /**
   * Cancela el débito automático. Lo que ya pagó (o la prueba) lo sigue
   * usando hasta que se venza; después, los días de gracia y la suspensión
   * de siempre.
   */
  @Transactional
  public BillingStatusResponse cancel(String businessId) {
    Business business = find(businessId);
    if (!hasLiveDebit(business)) throw new ConflictException("No tenés una suscripción activa para cancelar.");
    if (!properties.hasMercadoPago()) {
      throw new ConflictException("No se pudo contactar a Mercado Pago. Probá de nuevo en un rato.");
    }
    cancelDebit(business);
    return status(businessId);
  }

  /**
   * V17 — "Botón de baja" / "Botón de arrepentimiento": corta el débito
   * automático si lo tiene. Devuelve si se canceló algo.
   *
   * <p>Sin {@code @Transactional} propio a propósito: corre dentro de la
   * transacción de quien lo llama, y si Mercado Pago falla, el que llama lo
   * ataja y sigue (el pedido de baja se guarda igual).
   */
  public boolean cancelDebitIfAny(Business business) {
    if (!hasLiveDebit(business) || !properties.hasMercadoPago()) return false;
    cancelDebit(business);
    return true;
  }

  private static boolean hasLiveDebit(Business business) {
    return business.getMpPreapprovalId() != null && LIVE_SUBSCRIPTION.contains(business.getMpPreapprovalStatus());
  }

  private void cancelDebit(Business business) {
    MpPreapproval cancelled = mercadoPago.cancelPreapproval(properties.mpAccessToken(), business.getMpPreapprovalId());
    business.setMpPreapprovalStatus(cancelled.status() == null ? "cancelled" : cancelled.status());
    businessRepository.save(business);
  }

  /** Al volver de Mercado Pago (o con "Actualizar"): pregunta cómo quedó, sin esperar el aviso. */
  @Transactional
  public BillingStatusResponse refresh(String businessId) {
    Business business = find(businessId);
    if (business.getMpPreapprovalId() != null && properties.hasMercadoPago()) {
      try {
        syncSubscription(business.getMpPreapprovalId());
      } catch (MercadoPagoApiException ex) {
        log.warn("No se pudo consultar la suscripción de {}: {}", businessId, ex.getMessage());
      }
    }
    return status(businessId);
  }

  // ---------------------------------------------------------------------
  // Avisos de Mercado Pago y proceso de fondo
  // ---------------------------------------------------------------------

  /** Aviso de Mercado Pago ({@code type}: subscription_preapproval o subscription_authorized_payment). */
  @Transactional
  public void handleWebhook(String type, String id) {
    if (id == null || id.isBlank() || !properties.hasMercadoPago()) return;
    if ("subscription_authorized_payment".equals(type)) {
      processAuthorizedPayment(mercadoPago.getAuthorizedPayment(properties.mpAccessToken(), id));
    } else if ("subscription_preapproval".equals(type) || "preapproval".equals(type)) {
      syncSubscription(id);
    }
  }

  /**
   * Lo que corre cada hora: se pone al día con Mercado Pago (por si se
   * perdió algún aviso) y suspende a los que se les terminaron los días de
   * gracia.
   */
  public void runChecks() {
    if (properties.hasMercadoPago()) {
      for (Business business : businessRepository.findAllByMpPreapprovalIdIsNotNull()) {
        if (!LIVE_SUBSCRIPTION.contains(business.getMpPreapprovalStatus())) continue;
        try {
          syncSubscription(business.getMpPreapprovalId());
        } catch (RuntimeException ex) {
          log.warn("No se pudo revisar la suscripción del café {}: {}", business.getId(), ex.getMessage());
        }
      }
    }
    suspendOverdue();
  }

  /** Suspende a los cafés con los días de gracia vencidos. Devuelve cuántos. */
  @Transactional
  public int suspendOverdue() {
    int graceDays = settings().getGraceDays();
    Instant now = Instant.now();
    int count = 0;
    for (Business business : businessRepository.findAll()) {
      BillingState state = BillingState.of(business, graceDays, now);
      if ("PAST_DUE".equals(state.state()) && state.suspendsAt() != null && !now.isBefore(state.suspendsAt())) {
        business.setStatus(BusinessStatus.SUSPENDED);
        business.setBillingSuspended(true);
        business.setStatusReason(PAYMENT_REASON);
        businessRepository.save(business);
        actionRepository.save(systemAction(business.getId(), "Suspendido automáticamente: " + PAYMENT_REASON.toLowerCase()));
        log.info("Café {} suspendido por falta de pago.", business.getId());
        count++;
      }
    }
    return count;
  }

  /** Trae la suscripción y todos sus débitos, y los aplica. */
  @Transactional
  public void syncSubscription(String preapprovalId) {
    MpPreapproval preapproval = mercadoPago.getPreapproval(properties.mpAccessToken(), preapprovalId);
    Business business = businessFor(preapproval);
    if (business == null) return;

    // La suscripción que está vigente pasa a ser "la" del café (por si el
    // dueño abrió dos veces "Suscribirme" y confirmó la primera).
    if (preapprovalId.equals(business.getMpPreapprovalId()) || "authorized".equals(preapproval.status())) {
      business.setMpPreapprovalId(preapprovalId);
      business.setMpPreapprovalStatus(preapproval.status());
      if (preapproval.payerEmail() != null && !preapproval.payerEmail().isBlank()) {
        business.setMpPayerEmail(preapproval.payerEmail());
      }
      businessRepository.save(business);
    }
    for (MpAuthorizedPayment payment : mercadoPago.searchAuthorizedPayments(properties.mpAccessToken(), preapprovalId)) {
      applyPayment(business, payment);
    }
  }

  @Transactional
  public void processAuthorizedPayment(MpAuthorizedPayment payment) {
    if (payment == null || payment.preapprovalId() == null) return;
    Business business = businessRepository.findByMpPreapprovalId(payment.preapprovalId()).orElse(null);
    if (business == null) {
      business = businessFor(mercadoPago.getPreapproval(properties.mpAccessToken(), payment.preapprovalId()));
    }
    if (business != null) applyPayment(business, payment);
  }

  /**
   * Anota el débito (una sola vez por débito de Mercado Pago; si antes se
   * había rechazado y ahora se aprobó en un reintento, se actualiza) y, si
   * se aprobó, deja el café pago un mes más y lo reactiva si estaba
   * suspendido por falta de pago.
   */
  private void applyPayment(Business business, MpAuthorizedPayment payment) {
    String result = paymentResult(payment);
    if (result == null) return; // todavía no se intentó cobrar

    SubscriptionPayment record = paymentRepository.findByMpAuthorizedPaymentId(payment.id()).orElse(null);
    if (record != null && "approved".equals(record.getStatus())) return; // ya aplicado
    boolean isNew = record == null;
    if (isNew) {
      record = new SubscriptionPayment();
      record.setBusinessId(business.getId());
      record.setMpAuthorizedPaymentId(payment.id());
    }
    record.setMpPaymentId(payment.paymentId());
    record.setAmount(payment.amount());
    record.setStatus(result);
    record.setStatusDetail(truncate(payment.paymentStatusDetail(), 100));
    record.setChargedAt(payment.debitDate() == null ? Instant.now() : payment.debitDate());

    if ("approved".equals(result)) {
      Instant from = BillingState.accessUntil(business);
      if (from == null || from.isBefore(record.getChargedAt())) from = record.getChargedAt();
      Instant coversUntil = from.atZone(ZONE).plusMonths(1).toInstant();
      record.setCoversUntil(coversUntil);
      business.setPaidUntil(coversUntil);
      if (business.getPlan() == BusinessPlan.TRIAL) business.setPlan(BusinessPlan.BASIC);
      if (business.isBlockedForPayment()) {
        business.setStatus(BusinessStatus.ACTIVE);
        business.setBillingSuspended(false);
        business.setStatusReason(null);
        actionRepository.save(systemAction(business.getId(), "Reactivado automáticamente: pago recibido"));
      } else if (business.getStatus() == BusinessStatus.TRIAL) {
        business.setStatus(BusinessStatus.ACTIVE);
      }
      businessRepository.save(business);
    }
    try {
      paymentRepository.saveAndFlush(record);
    } catch (DataIntegrityViolationException ex) {
      // Llegaron dos avisos del mismo débito a la vez: el otro ya lo anotó.
      if (!isNew) throw ex;
    }
  }

  /** approved, rejected o null (todavía no se intentó). */
  private static String paymentResult(MpAuthorizedPayment payment) {
    String paymentStatus = payment.paymentStatus();
    if ("approved".equals(paymentStatus)) return "approved";
    if (paymentStatus == null) return null;
    if ("rejected".equals(paymentStatus) || "cancelled".equals(paymentStatus)) return "rejected";
    return null; // in_process, pending…: se verá en el próximo aviso
  }

  private Business businessFor(MpPreapproval preapproval) {
    if (preapproval == null) return null;
    Business business = null;
    if (preapproval.id() != null) business = businessRepository.findByMpPreapprovalId(preapproval.id()).orElse(null);
    if (business == null && preapproval.externalReference() != null) {
      business = businessRepository.findById(preapproval.externalReference()).orElse(null);
    }
    return business;
  }

  // ---------------------------------------------------------------------
  // Panel de Fran
  // ---------------------------------------------------------------------

  /** "Sin cobro" (cortesía): si estaba suspendido por falta de pago, lo reactiva. */
  @Transactional
  public String setExempt(Business business, boolean exempt) {
    if (Boolean.valueOf(exempt).equals(business.getBillingExempt())) return null;
    business.setBillingExempt(exempt);
    if (exempt && business.isBlockedForPayment()) {
      business.setStatus(BusinessStatus.ACTIVE);
      business.setBillingSuspended(false);
      business.setStatusReason(null);
    }
    return exempt ? "Sin cobro (cortesía)" : "Vuelve a pagar suscripción";
  }

  /** Suma días de prueba (desde hoy si ya se había vencido). Si estaba suspendido por falta de pago, lo reactiva. */
  @Transactional
  public String extendTrial(Business business, int days) {
    Instant base = BillingState.accessUntil(business);
    Instant now = Instant.now();
    if (base == null || base.isBefore(now)) base = now;
    Instant trialEnd = base.plus(Duration.ofDays(days));
    business.setTrialEndsAt(trialEnd);
    if (business.isBlockedForPayment()) {
      business.setStatus(BusinessStatus.TRIAL);
      business.setBillingSuspended(false);
      business.setStatusReason(null);
    }
    return "Prueba extendida " + days + (days == 1 ? " día" : " días");
  }

  // ---------------------------------------------------------------------

  private static boolean isOverdue(BillingState state) {
    return "PAST_DUE".equals(state.state()) || "SUSPENDED".equals(state.state());
  }

  private Business find(String businessId) {
    return businessRepository.findById(businessId).orElseThrow(() -> new NotFoundException("No se encontró el café."));
  }

  private static PlatformAction systemAction(String businessId, String detail) {
    PlatformAction action = new PlatformAction();
    action.setAdminId(null);
    action.setBusinessId(businessId);
    action.setAction("BILLING");
    action.setDetail(detail);
    return action;
  }

  private static String displayName(Business business) {
    return business.getTradeName() != null && !business.getTradeName().isBlank() ? business.getTradeName() : business.getLegalName();
  }

  private static String truncate(String value, int max) {
    return value == null || value.length() <= max ? value : value.substring(0, max);
  }
}
