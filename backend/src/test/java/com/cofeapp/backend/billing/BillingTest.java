package com.cofeapp.backend.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.cofeapp.backend.business.BusinessRepository;
import com.cofeapp.backend.domain.Business;
import com.cofeapp.backend.mercadopago.client.MercadoPagoClient.MpPreapprovalRequest;
import com.cofeapp.backend.support.FakeMercadoPagoClient;
import com.cofeapp.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Cobro de la suscripción (V16): prueba gratis, débito automático, gracia y suspensión. */
@DisplayName("Cobro de la suscripción")
class BillingTest extends IntegrationTest {

  private static final String WEBHOOK = "/webhooks/mercadopago-suscripciones";

  @Autowired private FakeMercadoPagoClient mercadoPago;
  @Autowired private BusinessRepository businessRepository;
  @Autowired private BillingService billingService;

  @Test
  @DisplayName("Un café nuevo tiene 14 días de prueba; sin precio cargado no se puede suscribir; con precio, el dueño se suscribe desde el fin de la prueba")
  void trialAndCheckout() throws Exception {
    String admin = adminToken();
    setPrice(admin, null);
    Tenant cafe = newBusiness();
    Staff mozo = newStaff(cafe, "WAITER");
    Staff administrador = newStaff(cafe, "ADMIN");

    JsonNode status = get(cafe.token(), "/subscription");
    assertThat(status.get("state").asText()).isEqualTo("TRIAL");
    assertThat(status.get("daysLeft").asLong()).isEqualTo(14);
    assertThat(status.get("canSubscribe").asBoolean()).isFalse();
    assertThat(status.get("unavailableReason").asText()).contains("Todavía no está disponible");
    post(cafe.token(), "/subscription/checkout", Map.of("payerEmail", cafe.email()), 409);

    get(mozo.token(), "/subscription", 403);
    get(administrador.token(), "/subscription", 200);

    setPrice(admin, "25000");
    post(administrador.token(), "/subscription/checkout", Map.of("payerEmail", cafe.email()), 403);
    post(cafe.token(), "/subscription/checkout", Map.of("payerEmail", "no-es-un-mail"), 400);

    JsonNode checkout = post(cafe.token(), "/subscription/checkout", Map.of("payerEmail", "Pagos@MiCafe.com"), 200);
    String url = checkout.get("checkoutUrl").asText();
    assertThat(url).contains("mercadopago.com.ar/subscriptions/checkout?preapproval_id=");
    MpPreapprovalRequest sent = mercadoPago.preapprovalRequest(url.substring(url.indexOf('=') + 1));
    assertThat(sent.amount()).isEqualByComparingTo("25000");
    assertThat(sent.payerEmail()).isEqualTo("pagos@micafe.com");
    assertThat(sent.externalReference()).isEqualTo(cafe.businessId());
    assertThat(sent.backUrl()).endsWith("/suscripcion?volviste=1");
    Instant trialEnd = business(cafe).getTrialEndsAt();
    assertThat(sent.startDate()).isCloseTo(trialEnd, within(1, ChronoUnit.SECONDS)); // primer débito: cuando termina la prueba

    JsonNode after = get(cafe.token(), "/subscription");
    assertThat(after.get("subscriptionStatus").asText()).isEqualTo("pending");
    assertThat(after.get("payerEmail").asText()).isEqualTo("pagos@micafe.com");
  }

  @Test
  @DisplayName("Con el débito aprobado queda pago un mes desde el fin de la prueba; un aviso repetido no lo cuenta dos veces")
  void approvedPayment() throws Exception {
    String admin = adminToken();
    setPrice(admin, "25000");
    Tenant cafe = newBusiness();
    String preapproval = subscribe(cafe);
    Instant trialEnd = business(cafe).getTrialEndsAt();

    mercadoPago.setPreapprovalStatus(preapproval, "authorized");
    webhook("subscription_preapproval", preapproval);
    assertThat(get(cafe.token(), "/subscription").get("subscriptionStatus").asText()).isEqualTo("authorized");

    String debit = mercadoPago.charge(preapproval, "AP-" + unique(), "approved", new BigDecimal("25000"), trialEnd);
    webhook("subscription_authorized_payment", debit);
    webhook("subscription_authorized_payment", debit);

    JsonNode status = get(cafe.token(), "/subscription");
    assertThat(status.get("state").asText()).isEqualTo("ACTIVE");
    assertThat(status.get("payments")).hasSize(1);
    assertThat(status.get("payments").get(0).get("status").asText()).isEqualTo("approved");
    Instant paidUntil = Instant.parse(status.get("paidUntil").asText());
    assertThat(Duration.between(trialEnd, paidUntil).toDays()).isBetween(28L, 31L);
    assertThat(business(cafe).getStatus().name()).isEqualTo("ACTIVE");

    JsonNode again = post(cafe.token(), "/subscription/checkout", Map.of("payerEmail", cafe.email()), 409);
    assertThat(again.get("message").asText()).contains("Ya estás suscripto");
  }

  @Test
  @DisplayName("Vencido: 7 días con aviso, después se suspende solo; el dueño entra solo a pagar y al pagar se reactiva")
  void graceSuspensionAndReactivation() throws Exception {
    String admin = adminToken();
    setPrice(admin, "25000");
    Tenant cafe = newBusiness();
    Staff mozo = newStaff(cafe, "WAITER");

    moveTrialEnd(cafe, Duration.ofDays(-2));
    billingService.runChecks();
    JsonNode pastDue = get(cafe.token(), "/subscription");
    assertThat(pastDue.get("state").asText()).isEqualTo("PAST_DUE");
    assertThat(pastDue.get("daysLeft").asLong()).isEqualTo(5);
    get(mozo.token(), "/products", 200); // durante la gracia se usa normal

    moveTrialEnd(cafe, Duration.ofDays(-8));
    billingService.runChecks();
    assertThat(business(cafe).isBlockedForPayment()).isTrue();

    // El equipo queda afuera, con un mensaje claro.
    JsonNode cut = get(mozo.token(), "/products", 401);
    assertThat(cut.get("message").asText()).contains("falta de pago");
    JsonNode denied = post(null, "/auth/login", Map.of("email", mozo.email(), "password", PASSWORD), 403);
    assertThat(denied.get("message").asText()).contains("falta de pago", "El dueño puede entrar");

    // El dueño entra, pero solo a Suscripción (402 en lo demás, sin cerrarle la sesión).
    String owner = login(cafe.email(), PASSWORD);
    JsonNode blocked = get(owner, "/products", 402);
    assertThat(blocked.get("message").asText()).contains("Pagá la suscripción");
    get(cafe.token(), "/orders", 402);
    assertThat(get(owner, "/subscription").get("state").asText()).isEqualTo("SUSPENDED");

    // Paga: el primer débito es ya (no la fecha vieja de la prueba).
    String preapproval = subscribe(cafe, owner);
    Instant start = mercadoPago.preapprovalRequest(preapproval).startDate();
    assertThat(start).isBetween(Instant.now(), Instant.now().plus(Duration.ofMinutes(15)));
    mercadoPago.setPreapprovalStatus(preapproval, "authorized");
    webhook("subscription_authorized_payment", mercadoPago.charge(preapproval, "AP-" + unique(), "approved", new BigDecimal("25000"), Instant.now()));

    assertThat(business(cafe).getStatus().name()).isEqualTo("ACTIVE");
    get(owner, "/products", 200);
    login(mozo.email(), PASSWORD);
    JsonNode status = get(owner, "/subscription");
    assertThat(status.get("state").asText()).isEqualTo("ACTIVE");
    assertThat(Duration.between(Instant.now(), Instant.parse(status.get("paidUntil").asText())).toDays()).isBetween(27L, 31L);

    JsonNode history = get(admin, "/platform/businesses/" + cafe.businessId()).get("history");
    assertThat(history.toString()).contains("Suspendido automáticamente", "Reactivado automáticamente: pago recibido");
  }

  @Test
  @DisplayName("Suscripto pero con la tarjeta rechazada y vencido: puede suscribirse de nuevo con otra, y se cancela la anterior")
  void resubscribeWithAnotherCard() throws Exception {
    setPrice(adminToken(), "25000");
    Tenant cafe = newBusiness();
    String first = subscribe(cafe);
    mercadoPago.setPreapprovalStatus(first, "authorized");
    webhook("subscription_preapproval", first);
    assertThat(get(cafe.token(), "/subscription").get("canSubscribe").asBoolean()).isFalse(); // al día: no hace falta

    moveTrialEnd(cafe, Duration.ofDays(-3));
    webhook("subscription_authorized_payment", mercadoPago.charge(first, "AP-" + unique(), "rejected", new BigDecimal("25000"), Instant.now()));
    JsonNode overdue = get(cafe.token(), "/subscription");
    assertThat(overdue.get("state").asText()).isEqualTo("PAST_DUE");
    assertThat(overdue.get("canSubscribe").asBoolean()).isTrue();

    String second = subscribe(cafe);
    assertThat(second).isNotEqualTo(first);
    assertThat(mercadoPago.getPreapproval(FakeMercadoPagoClient.BILLING_TOKEN, first).status()).isEqualTo("cancelled");
    assertThat(mercadoPago.preapprovalRequest(second).startDate()).isBefore(Instant.now().plus(Duration.ofMinutes(15)));
  }

  @Test
  @DisplayName("Un débito rechazado queda anotado; si Mercado Pago lo reintenta y se aprueba, se actualiza sin duplicarse")
  void rejectedThenApproved() throws Exception {
    setPrice(adminToken(), "25000");
    Tenant cafe = newBusiness();
    String preapproval = subscribe(cafe);
    mercadoPago.setPreapprovalStatus(preapproval, "authorized");
    String debit = "AP-" + unique();

    webhook("subscription_authorized_payment", mercadoPago.charge(preapproval, debit, "rejected", new BigDecimal("25000"), Instant.now()));
    JsonNode rejected = get(cafe.token(), "/subscription");
    assertThat(rejected.get("payments")).hasSize(1);
    assertThat(rejected.get("payments").get(0).get("status").asText()).isEqualTo("rejected");
    assertThat(rejected.get("paidUntil").isNull()).isTrue();

    mercadoPago.charge(preapproval, debit, "approved", new BigDecimal("25000"), Instant.now());
    billingService.runChecks(); // sin aviso: el proceso de fondo se pone al día solo
    JsonNode approved = get(cafe.token(), "/subscription");
    assertThat(approved.get("payments")).hasSize(1);
    assertThat(approved.get("payments").get(0).get("status").asText()).isEqualTo("approved");
    assertThat(approved.get("paidUntil").isNull()).isFalse();
  }

  @Test
  @DisplayName("Cancelar corta el débito pero deja usar lo que ya tiene; \"Sin cobro\" y sumar días de prueba desde el panel")
  void cancelExemptAndExtend() throws Exception {
    String admin = adminToken();
    setPrice(admin, "25000");
    Tenant cafe = newBusiness();
    String preapproval = subscribe(cafe);
    mercadoPago.setPreapprovalStatus(preapproval, "authorized");
    webhook("subscription_preapproval", preapproval);

    JsonNode cancelled = post(cafe.token(), "/subscription/cancel", null, 200);
    assertThat(cancelled.get("subscriptionStatus").asText()).isEqualTo("cancelled");
    assertThat(cancelled.get("state").asText()).isEqualTo("TRIAL"); // sigue en su prueba
    post(cafe.token(), "/subscription/cancel", null, 409);

    // "Sin cobro": vencido y todo, no se suspende.
    Tenant amigo = newBusiness();
    patch(admin, "/platform/businesses/" + amigo.businessId(), Map.of("billingExempt", true), 200);
    moveTrialEnd(amigo, Duration.ofDays(-30));
    billingService.runChecks();
    assertThat(get(amigo.token(), "/subscription").get("state").asText()).isEqualTo("EXEMPT");
    get(amigo.token(), "/products", 200);

    // Sumar días de prueba a uno suspendido por falta de pago: vuelve a entrar.
    Tenant atrasado = newBusiness();
    moveTrialEnd(atrasado, Duration.ofDays(-10));
    billingService.runChecks();
    assertThat(business(atrasado).isBlockedForPayment()).isTrue();
    JsonNode extended = patch(admin, "/platform/businesses/" + atrasado.businessId(), Map.of("extendTrialDays", 7), 200);
    assertThat(extended.get("billing").get("state").asText()).isEqualTo("TRIAL");
    assertThat(extended.get("history").toString()).contains("Prueba extendida 7 días");
    get(login(atrasado.email(), PASSWORD), "/products", 200);
  }

  @Test
  @DisplayName("Una suspensión a mano de Fran no la levanta un pago; y los avisos inventados no marcan nada")
  void manualSuspensionAndFakeWebhooks() throws Exception {
    String admin = adminToken();
    setPrice(admin, "25000");
    Tenant cafe = newBusiness();
    String preapproval = subscribe(cafe);
    mercadoPago.setPreapprovalStatus(preapproval, "authorized");

    patch(admin, "/platform/businesses/" + cafe.businessId(), Map.of("status", "SUSPENDED", "statusReason", "Pidió la baja"), 200);
    webhook("subscription_authorized_payment", mercadoPago.charge(preapproval, "AP-" + unique(), "approved", new BigDecimal("25000"), Instant.now()));
    assertThat(business(cafe).getStatus().name()).isEqualTo("SUSPENDED");
    post(null, "/auth/login", Map.of("email", cafe.email(), "password", PASSWORD), 403);

    // Aviso con un id que Mercado Pago no conoce: responde 200 y no pasa nada.
    webhook("subscription_authorized_payment", "AP-inventado");
    webhook("subscription_preapproval", "PRE-inventado");
    post(null, WEBHOOK, Map.of("type", "otra-cosa"), 200);

    // Los cafés no tocan la configuración del cobro.
    call(org.springframework.http.HttpMethod.PUT, "/platform/settings", newBusiness().token(), settings("1"), 403);
  }

  // ---------------------------------------------------------------------

  private String adminToken() throws Exception {
    return login("superadmin@test.local", "clave-superadmin-1");
  }

  private static Map<String, Object> settings(String price) {
    Map<String, Object> body = new HashMap<>();
    body.put("monthlyPrice", price == null ? null : new BigDecimal(price));
    body.put("trialDays", 14);
    body.put("graceDays", 7);
    return body;
  }

  private void setPrice(String admin, String price) throws Exception {
    JsonNode saved = call(org.springframework.http.HttpMethod.PUT, "/platform/settings", admin, settings(price), 200);
    assertThat(saved.get("mercadoPagoConfigured").asBoolean()).isTrue();
  }

  private String subscribe(Tenant cafe) throws Exception {
    return subscribe(cafe, cafe.token());
  }

  private String subscribe(Tenant cafe, String ownerToken) throws Exception {
    String url = post(ownerToken, "/subscription/checkout", Map.of("payerEmail", cafe.email()), 200).get("checkoutUrl").asText();
    return url.substring(url.indexOf('=') + 1);
  }

  private void webhook(String type, String id) throws Exception {
    post(null, WEBHOOK, Map.of("type", type, "data", Map.of("id", id)), 200);
  }

  private Business business(Tenant cafe) {
    return businessRepository.findById(cafe.businessId()).orElseThrow();
  }

  /** Corre el fin de la prueba a "ahora + delta" (negativo = ya se venció). */
  private void moveTrialEnd(Tenant cafe, Duration delta) {
    Business business = business(cafe);
    business.setTrialEndsAt(Instant.now().plus(delta));
    businessRepository.save(business);
  }
}
