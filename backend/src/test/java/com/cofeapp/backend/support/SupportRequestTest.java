package com.cofeapp.backend.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.cofeapp.backend.business.BusinessRepository;
import com.cofeapp.backend.domain.Business;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

/** Botón de soporte (V18): WhatsApp de Fran, pedidos de ayuda y su lista en el panel. */
@DisplayName("Botón de soporte")
class SupportRequestTest extends IntegrationTest {

  @Autowired private BusinessRepository businessRepository;
  @Autowired private com.cofeapp.backend.billing.BillingService billingService;

  @Test
  @DisplayName("El WhatsApp de soporte se carga en el panel y se ve sin iniciar sesión (lo usa también el login)")
  void info() throws Exception {
    String admin = adminToken();
    Map<String, Object> settings = settings("5492235550000");
    call(HttpMethod.PUT, "/platform/settings", admin, settings, 200);
    JsonNode info = get(null, "/support/info");
    assertThat(info.get("whatsapp").asText()).isEqualTo("5492235550000");
    assertThat(info.get("contact").asText()).isEqualTo("Fran (223 555-0000)");
    assertThat(get(admin, "/platform/settings").get("supportWhatsapp").asText()).isEqualTo("5492235550000");

    // Solo números, 8 a 15.
    call(HttpMethod.PUT, "/platform/settings", admin, settings("+54 9 223"), 400);
    call(HttpMethod.PUT, "/platform/settings", admin, settings(""), 200);
    assertThat(get(null, "/support/info").get("whatsapp").isNull()).isTrue();
  }

  @Test
  @DisplayName("Cualquiera del café pide ayuda; queda en el panel con los datos técnicos y Fran lo marca resuelto")
  void requestAndPanel() throws Exception {
    Tenant cafe = newBusiness();
    Staff mozo = newStaff(cafe, "WAITER");
    Map<String, Object> body = new HashMap<>();
    body.put("message", "Se quedó en blanco la pantalla de pedidos");
    body.put("page", "/pedidos");
    body.put("lastError", "TypeError: x is undefined");
    body.put("userAgent", "Mozilla/5.0 (Android)");
    JsonNode created = post(mozo.token(), "/support/requests", body, 201);
    assertThat(created.get("userRole").asText()).isEqualTo("WAITER");
    assertThat(created.get("userName").asText()).isEqualTo("Empleado WAITER");
    assertThat(created.get("status").asText()).isEqualTo("PENDING");
    assertThat(mails.sentTo("superadmin@test.local"))
        .anySatisfy(m -> assertThat(m.body()).contains("Se quedó en blanco", "/pedidos", "TypeError"));

    // Sin nada escrito también vale (es "¡se trabó!").
    post(cafe.token(), "/support/requests", Map.of(), 201);
    post(null, "/support/requests", Map.of(), 401);

    String admin = adminToken();
    JsonNode listed = find(get(admin, "/platform/support"), id(created));
    assertThat(listed.get("businessId").asText()).isEqualTo(cafe.businessId());
    assertThat(listed.get("businessName").asText()).startsWith("Café de prueba");
    assertThat(listed.get("lastError").asText()).isEqualTo("TypeError: x is undefined");

    JsonNode done = patch(admin, "/platform/support/" + id(created), Map.of("status", "DONE", "adminNote", "Era el wifi."), 200);
    assertThat(done.get("status").asText()).isEqualTo("DONE");
    assertThat(done.get("handledAt").isNull()).isFalse();
    assertThat(done.get("adminNote").asText()).isEqualTo("Era el wifi.");
    patch(admin, "/platform/support/" + id(created), Map.of("status", "OTRA"), 400);

    // Los cafés no ven la lista.
    get(cafe.token(), "/platform/support", 403);
    patch(cafe.token(), "/platform/support/" + id(created), Map.of("status", "DONE"), 403);
  }

  @Test
  @DisplayName("Freno: hasta 10 pedidos por hora por persona")
  void rateLimit() throws Exception {
    Tenant cafe = newBusiness();
    for (int i = 0; i < 10; i++) post(cafe.token(), "/support/requests", Map.of("message", "intento " + i), 201);
    JsonNode tooMany = post(cafe.token(), "/support/requests", Map.of(), 429);
    assertThat(tooMany.get("message").asText()).contains("varios pedidos de ayuda");
    // Otra persona del mismo café sí puede.
    post(newStaff(cafe, "KITCHEN").token(), "/support/requests", Map.of(), 201);
  }

  @Test
  @DisplayName("El dueño de un café suspendido por falta de pago igual puede pedir ayuda")
  void suspendedOwnerCanAskForHelp() throws Exception {
    String admin = adminToken();
    Map<String, Object> settings = settings(null);
    settings.put("monthlyPrice", new BigDecimal("25000"));
    call(HttpMethod.PUT, "/platform/settings", admin, settings, 200);
    Tenant cafe = newBusiness();
    Business business = businessRepository.findById(cafe.businessId()).orElseThrow();
    business.setTrialEndsAt(Instant.now().minus(Duration.ofDays(30)));
    businessRepository.save(business);
    billingService.runChecks();
    assertThat(businessRepository.findById(cafe.businessId()).orElseThrow().isBlockedForPayment()).isTrue();

    String owner = login(cafe.email(), PASSWORD);
    get(owner, "/products", 402);
    post(owner, "/support/requests", Map.of("message", "No puedo pagar"), 201);
  }

  // ---------------------------------------------------------------------

  private String adminToken() throws Exception {
    return login("superadmin@test.local", "clave-superadmin-1");
  }

  private static Map<String, Object> settings(String whatsapp) {
    Map<String, Object> body = new HashMap<>();
    body.put("trialDays", 14);
    body.put("graceDays", 7);
    body.put("supportWhatsapp", whatsapp);
    return body;
  }

  private static JsonNode find(JsonNode list, String id) {
    for (JsonNode n : list) if (id(n).equals(id)) return n;
    throw new AssertionError("No está el pedido " + id);
  }
}
