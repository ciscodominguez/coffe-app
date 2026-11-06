package com.cofeapp.backend.legal;

import static org.assertj.core.api.Assertions.assertThat;

import com.cofeapp.backend.business.BusinessRepository;
import com.cofeapp.backend.domain.Business;
import com.cofeapp.backend.support.FakeMercadoPagoClient;
import com.cofeapp.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Los papeles (V17): aceptar términos y privacidad, y los botones de arrepentimiento y de baja. */
@DisplayName("Términos, privacidad y botones de baja")
class LegalTest extends IntegrationTest {

  @Autowired private BusinessRepository businessRepository;
  @Autowired private LegalAcceptanceRepository acceptanceRepository;
  @Autowired private FakeMercadoPagoClient mercadoPago;

  @Test
  @DisplayName("Sin la casilla no se puede registrar; con la casilla queda la constancia (versión, quién, cuándo)")
  void registrationRequiresAcceptance() throws Exception {
    Map<String, Object> body = registration();
    body.remove("acceptTerms");
    JsonNode missing = post(null, "/auth/register-business", body, 400);
    assertThat(missing.toString()).contains("Tenés que aceptar los Términos");
    body.put("acceptTerms", false);
    post(null, "/auth/register-business", body, 400);

    String version = get(null, "/legal/version").get("version").asText(); // público
    Tenant cafe = newBusiness();
    JsonNode status = get(cafe.token(), "/legal/status");
    assertThat(status.get("currentVersion").asText()).isEqualTo(version);
    assertThat(status.get("acceptedVersion").asText()).isEqualTo(version);
    assertThat(status.get("mustAccept").asBoolean()).isFalse();
    assertThat(acceptanceRepository.findAllByBusinessIdOrderByAcceptedAtDesc(cafe.businessId()))
        .singleElement()
        .satisfies(a -> {
          assertThat(a.getUserId()).isEqualTo(cafe.userId());
          assertThat(a.getVersion()).isEqualTo(version);
          assertThat(a.getIp()).isNotBlank();
        });
  }

  @Test
  @DisplayName("Con una versión nueva, el dueño la tiene que aceptar (y solo él); una versión vieja no se acepta")
  void reacceptNewVersion() throws Exception {
    Tenant cafe = newBusiness();
    Staff mozo = newStaff(cafe, "WAITER");
    Business business = businessRepository.findById(cafe.businessId()).orElseThrow();
    business.setTermsVersion("2020-01-01"); // como si hubiera aceptado una versión anterior
    businessRepository.save(business);

    JsonNode status = get(cafe.token(), "/legal/status");
    assertThat(status.get("mustAccept").asBoolean()).isTrue();
    String current = status.get("currentVersion").asText();

    post(mozo.token(), "/legal/accept", Map.of("version", current), 403);
    JsonNode stale = post(cafe.token(), "/legal/accept", Map.of("version", "2020-01-01"), 409);
    assertThat(stale.get("message").asText()).contains("versión más nueva");

    JsonNode accepted = post(cafe.token(), "/legal/accept", Map.of("version", current), 200);
    assertThat(accepted.get("mustAccept").asBoolean()).isFalse();
    assertThat(acceptanceRepository.findAllByBusinessIdOrderByAcceptedAtDesc(cafe.businessId())).hasSize(2);
  }

  @Test
  @DisplayName("Botón de baja: sin entrar, da un código al instante, corta el débito automático y le llega a Fran")
  void cancellationButton() throws Exception {
    String admin = login("superadmin@test.local", "clave-superadmin-1");
    Map<String, Object> price = new HashMap<>();
    price.put("monthlyPrice", new BigDecimal("25000"));
    price.put("trialDays", 14);
    price.put("graceDays", 7);
    call(HttpMethod.PUT, "/platform/settings", admin, price, 200);

    Tenant cafe = newBusiness();
    String url = post(cafe.token(), "/subscription/checkout", Map.of("payerEmail", cafe.email()), 200).get("checkoutUrl").asText();
    String preapproval = url.substring(url.indexOf('=') + 1);
    mercadoPago.setPreapprovalStatus(preapproval, "authorized");
    post(null, "/webhooks/mercadopago-suscripciones", Map.of("type", "subscription_preapproval", "data", Map.of("id", preapproval)), 200);
    String taxId = businessRepository.findById(cafe.businessId()).orElseThrow().getTaxId();

    JsonNode created = request(form("CANCELLATION", cafe.email().toUpperCase(), taxId), ip(), 201);
    String code = created.get("code").asText();
    assertThat(code).matches("BAJ-[A-Z2-9]{6}");
    assertThat(mercadoPago.getPreapproval(FakeMercadoPagoClient.BILLING_TOKEN, preapproval).status()).isEqualTo("cancelled");
    assertThat(mails.sentTo(cafe.email())).anySatisfy(m -> assertThat(m.body()).contains(code));
    assertThat(mails.sentTo("superadmin@test.local")).anySatisfy(m -> assertThat(m.subject()).contains(code));

    JsonNode listed = find(get(admin, "/platform/requests"), code);
    assertThat(listed.get("businessId").asText()).isEqualTo(cafe.businessId());
    assertThat(listed.get("status").asText()).isEqualTo("PENDING");
    assertThat(listed.get("result").asText()).contains("Débito automático cancelado");

    JsonNode done = patch(admin, "/platform/requests/" + listed.get("id").asText(), Map.of("status", "DONE", "adminNote", "Le escribí."), 200);
    assertThat(done.get("status").asText()).isEqualTo("DONE");
    assertThat(done.get("handledAt").isNull()).isFalse();

    // Los cafés no ven los pedidos.
    get(cafe.token(), "/platform/requests", 403);
  }

  @Test
  @DisplayName("Botón de arrepentimiento: si el CUIT y el email no coinciden no toca nada; datos inválidos y abusos se frenan")
  void withdrawalAndValidation() throws Exception {
    Tenant cafe = newBusiness();
    String taxId = businessRepository.findById(cafe.businessId()).orElseThrow().getTaxId();

    // El email de otra persona con el CUIT del café: se anota, pero no se asocia ni se cancela nada.
    JsonNode created = request(form("WITHDRAWAL", "otra-persona@test.local", taxId), ip(), 201);
    assertThat(created.get("code").asText()).startsWith("ARR-");
    String admin = login("superadmin@test.local", "clave-superadmin-1");
    JsonNode listed = find(get(admin, "/platform/requests"), created.get("code").asText());
    assertThat(listed.get("businessId").isNull()).isTrue();
    assertThat(listed.get("result").asText()).contains("no coinciden");

    // Si Mercado Pago falla al cortar el débito, el pedido se guarda igual y avisa que hay que hacerlo a mano.
    Business business = businessRepository.findById(cafe.businessId()).orElseThrow();
    business.setMpPreapprovalId("PRE-que-mercado-pago-no-conoce");
    business.setMpPreapprovalStatus("authorized");
    businessRepository.save(business);
    JsonNode stillSaved = request(form("CANCELLATION", cafe.email(), taxId), ip(), 201);
    assertThat(find(get(admin, "/platform/requests"), stillSaved.get("code").asText()).get("result").asText())
        .contains("No se pudo cancelar el débito");

    request(form("OTRA_COSA", "a@b.com", null), ip(), 400);
    Map<String, Object> noEmail = form("CANCELLATION", "no-es-un-mail", null);
    request(noEmail, ip(), 400);

    String sameIp = ip();
    for (int i = 0; i < ServiceRequestService.MAX_PER_HOUR; i++) request(form("CANCELLATION", "x" + i + "@test.local", null), sameIp, 201);
    JsonNode blocked = request(form("CANCELLATION", "y@test.local", null), sameIp, 429);
    assertThat(blocked.get("message").asText()).contains("muchos pedidos");
  }

  // ---------------------------------------------------------------------

  private static Map<String, Object> registration() {
    String suffix = unique();
    Map<String, Object> body = new HashMap<>();
    body.put("legalName", "Café " + suffix);
    body.put("taxId", randomCuit());
    body.put("fiscalCondition", "MONOTRIBUTO");
    body.put("ownerEmail", "duenio-" + suffix + "@test.local");
    body.put("ownerPassword", PASSWORD);
    body.put("ownerFirstName", "Dueño");
    body.put("ownerLastName", "Prueba");
    body.put("acceptTerms", true);
    return body;
  }

  private static Map<String, Object> form(String type, String email, String taxId) {
    Map<String, Object> body = new HashMap<>();
    body.put("type", type);
    body.put("contactName", "Dueño Prueba");
    body.put("email", email);
    body.put("taxId", taxId);
    body.put("cafeName", "Mi café");
    body.put("message", "Quiero darme de baja.");
    return body;
  }

  /** Cada test usa su propia "IP" (el freno de 5 por hora es por IP). */
  private static String ip() {
    return "10." + (UUID.randomUUID().hashCode() & 0xff) + "." + (UUID.randomUUID().hashCode() & 0xff) + "." + (UUID.randomUUID().hashCode() & 0xff);
  }

  private JsonNode request(Map<String, Object> body, String ip, int expectedStatus) throws Exception {
    MvcResult result =
        mvc.perform(
                MockMvcRequestBuilders.post("/legal/requests")
                    .header("X-Forwarded-For", ip)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
            .andReturn();
    String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertThat(result.getResponse().getStatus()).as("POST /legal/requests respondió: %s", content).isEqualTo(expectedStatus);
    return json.readTree(content);
  }

  private static JsonNode find(JsonNode list, String code) {
    for (JsonNode row : list) {
      if (row.get("code").asText().equals(code)) return row;
    }
    throw new AssertionError("No aparece el pedido " + code);
  }
}
