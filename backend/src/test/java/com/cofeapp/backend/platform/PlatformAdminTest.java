package com.cofeapp.backend.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.cofeapp.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Panel de superadmin (V15): ver todos los cafés, suspender/activar y cambiar el plan. */
@DisplayName("Panel de superadmin")
class PlatformAdminTest extends IntegrationTest {

  private static final String ADMIN_EMAIL = "superadmin@test.local";
  private static final String ADMIN_PASSWORD = "clave-superadmin-1";

  @Test
  @DisplayName("El superadmin entra con su cuenta, ve todos los cafés con sus números, y no puede tocar las pantallas de un café")
  void loginAndList() throws Exception {
    JsonNode auth = post(null, "/auth/login", Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD), 200);
    assertThat(auth.get("user").get("role").asText()).isEqualTo("SUPERADMIN");
    assertThat(auth.get("user").get("businessId").isNull()).isTrue();
    String admin = auth.get("accessToken").asText();

    assertThat(get(admin, "/platform/me").get("email").asText()).isEqualTo(ADMIN_EMAIL);

    Tenant cafe = newBusiness();
    newStaff(cafe, "WAITER");
    String latte = newProduct(cafe, "Latte", "1500");
    openCash(cafe.token(), cafe.locationId(), "0");
    createOrder(cafe.token(), cafe.locationId(), null, item(latte, 1));
    createOrder(cafe.token(), cafe.locationId(), null, item(latte, 2));

    JsonNode row = find(get(admin, "/platform/businesses"), cafe.businessId());
    assertThat(row.get("ownerEmail").asText()).isEqualTo(cafe.email());
    assertThat(row.get("status").asText()).isEqualTo("TRIAL");
    assertThat(row.get("locations").asLong()).isEqualTo(1);
    assertThat(row.get("activeUsers").asLong()).isEqualTo(2);
    assertThat(row.get("ordersLast30Days").asLong()).isEqualTo(2);
    assertThat(row.get("lastOrderAt").isNull()).isFalse();

    JsonNode detail = get(admin, "/platform/businesses/" + cafe.businessId());
    assertThat(detail.get("users")).hasSize(2);
    assertThat(detail.get("locations")).hasSize(1);

    // El token de superadmin no sirve adentro de un café...
    get(admin, "/products", 403);
    get(admin, "/orders", 403);
    // ...y ningún usuario de un café entra al panel, ni el dueño.
    get(cafe.token(), "/platform/businesses", 403);
    get(null, "/platform/businesses", 401);
    get(admin, "/platform/businesses/no-existe", 404);
  }

  @Test
  @DisplayName("Contraseña mala da 401, y su email no se puede usar para registrar un café")
  void wrongPasswordAndReservedEmail() throws Exception {
    post(null, "/auth/login", Map.of("email", ADMIN_EMAIL, "password", "otra-clave-mala"), 401);

    Map<String, Object> body = new HashMap<>();
    body.put("legalName", "Café trucho");
    body.put("taxId", randomCuit());
    body.put("fiscalCondition", "MONOTRIBUTO");
    body.put("ownerEmail", ADMIN_EMAIL.toUpperCase());
    body.put("ownerPassword", PASSWORD);
    body.put("ownerFirstName", "X");
    body.put("ownerLastName", "Y");
    body.put("acceptTerms", true);
    post(null, "/auth/register-business", body, 409);
  }

  @Test
  @DisplayName("Suspender corta al instante las sesiones del café y el login avisa; reactivarlo lo deja entrar de nuevo")
  void suspendAndReactivate() throws Exception {
    String admin = adminToken();
    Tenant cafe = newBusiness();
    Staff mozo = newStaff(cafe, "WAITER");
    Tenant otro = newBusiness();

    JsonNode suspended =
        patch(admin, "/platform/businesses/" + cafe.businessId(), Map.of("status", "SUSPENDED", "statusReason", "Falta de pago"), 200);
    assertThat(suspended.get("summary").get("status").asText()).isEqualTo("SUSPENDED");

    // Las sesiones abiertas se cortan con el aviso.
    JsonNode cut = get(cafe.token(), "/products", 401);
    assertThat(cut.toString()).contains("suspendido", "Falta de pago", "Fran (223 555-0000)");
    get(mozo.token(), "/products", 401);

    // Login: contraseña bien, pero no entra y le dice por qué.
    JsonNode denied = post(null, "/auth/login", Map.of("email", cafe.email(), "password", PASSWORD), 403);
    assertThat(denied.get("message").asText()).contains("suspendido", "Falta de pago");
    // Con contraseña mala no revela nada del estado.
    JsonNode bad = post(null, "/auth/login", Map.of("email", cafe.email(), "password", "cualquier-cosa"), 401);
    assertThat(bad.toString()).doesNotContain("suspendido");

    // Otro café sigue como si nada.
    get(otro.token(), "/products", 200);

    // Reactivar: vuelve a entrar (con un login nuevo) y los datos siguen ahí.
    patch(admin, "/platform/businesses/" + cafe.businessId(), Map.of("status", "ACTIVE"), 200);
    String again = login(cafe.email(), PASSWORD);
    assertThat(get(again, "/locations")).hasSize(1);

    // Dar de baja también bloquea.
    patch(admin, "/platform/businesses/" + cafe.businessId(), Map.of("status", "CANCELLED"), 200);
    JsonNode cancelled = post(null, "/auth/login", Map.of("email", cafe.email(), "password", PASSWORD), 403);
    assertThat(cancelled.get("message").asText()).contains("dado de baja");
  }

  @Test
  @DisplayName("Cambiar el plan y las notas queda en el historial con quién lo hizo")
  void planNotesAndHistory() throws Exception {
    String admin = adminToken();
    Tenant cafe = newBusiness();

    JsonNode after =
        patch(
            admin,
            "/platform/businesses/" + cafe.businessId(),
            Map.of("plan", "PRO", "adminNotes", "Cliente de Mar del Plata, 2 locales"),
            200);
    assertThat(after.get("summary").get("plan").asText()).isEqualTo("PRO");
    assertThat(after.get("adminNotes").asText()).isEqualTo("Cliente de Mar del Plata, 2 locales");

    patch(admin, "/platform/businesses/" + cafe.businessId(), Map.of("status", "SUSPENDED", "statusReason", "Pidió pausa"), 200);
    JsonNode detail = get(admin, "/platform/businesses/" + cafe.businessId());
    JsonNode history = detail.get("history");
    assertThat(history).hasSize(3);
    assertThat(history.toString()).contains("Prueba → Pro", "Notas actualizadas", "En prueba → Suspendido: Pidió pausa", ADMIN_EMAIL);

    // Mandar lo mismo otra vez no ensucia el historial.
    patch(admin, "/platform/businesses/" + cafe.businessId(), Map.of("plan", "PRO", "status", "SUSPENDED"), 200);
    assertThat(get(admin, "/platform/businesses/" + cafe.businessId()).get("history")).hasSize(3);

    // Valores inválidos se rechazan.
    patch(admin, "/platform/businesses/" + cafe.businessId(), Map.of("plan", "GRATIS_PARA_SIEMPRE"), 400);
    // El plan del café (etiqueta) lo ve el dueño igual que antes: el café sigue funcionando.
    patch(admin, "/platform/businesses/" + cafe.businessId(), Map.of("status", "ACTIVE"), 200);
    login(cafe.email(), PASSWORD);
  }

  // ---------------------------------------------------------------------

  private String adminToken() throws Exception {
    return login(ADMIN_EMAIL, ADMIN_PASSWORD);
  }

  private static JsonNode find(JsonNode list, String businessId) {
    for (JsonNode row : list) {
      if (row.get("id").asText().equals(businessId)) return row;
    }
    throw new AssertionError("No aparece el café " + businessId);
  }
}
