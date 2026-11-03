package com.cofeapp.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.cofeapp.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Cuentas y sesiones: login, bloqueo por intentos, contraseñas y cierre de sesión al instante. */
@DisplayName("Cuentas y sesiones")
class AuthSecurityTest extends IntegrationTest {

  private static final String NEW_PASSWORD = "clave-nueva-2026";

  @Test
  @DisplayName("Sin sesión (o con un token trucho) no se accede a nada")
  void needsValidToken() throws Exception {
    get(null, "/orders", 401);
    get("esto-no-es-un-token", "/orders", 401);
  }

  @Test
  @DisplayName("Login correcto devuelve la sesión; contraseña mala da 401")
  void login() throws Exception {
    Tenant cafe = newBusiness();

    JsonNode auth = post(null, "/auth/login", Map.of("email", cafe.email(), "password", PASSWORD), 200);
    assertThat(auth.get("user").get("role").asText()).isEqualTo("OWNER");
    assertThat(auth.get("user").get("businessId").asText()).isEqualTo(cafe.businessId());

    post(null, "/auth/login", Map.of("email", cafe.email(), "password", "cualquiera"), 401);
    post(null, "/auth/login", Map.of("email", "nadie-" + unique() + "@test.local", "password", PASSWORD), 401);
  }

  @Test
  @DisplayName("5 contraseñas mal seguidas bloquean ese mail, aunque después pongan la correcta")
  void lockAfterFailures() throws Exception {
    Tenant cafe = newBusiness();

    for (int i = 0; i < 5; i++) {
      post(null, "/auth/login", Map.of("email", cafe.email(), "password", "mal-" + i), 401);
    }

    post(null, "/auth/login", Map.of("email", cafe.email(), "password", PASSWORD), 429);
  }

  @Test
  @DisplayName("Cambiar la contraseña: la sesión vieja se corta y la nueva anda")
  void changeOwnPassword() throws Exception {
    Tenant cafe = newBusiness();
    String otraSesion = login(cafe.email(), PASSWORD); // por ejemplo, el celular

    JsonNode auth =
        post(
            cafe.token(),
            "/auth/change-password",
            Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD),
            200);
    String nuevoToken = auth.get("accessToken").asText();

    get(cafe.token(), "/auth/me", 401);
    get(otraSesion, "/auth/me", 401);
    get(nuevoToken, "/auth/me", 200);
    login(cafe.email(), NEW_PASSWORD);
    post(null, "/auth/login", Map.of("email", cafe.email(), "password", PASSWORD), 401);
  }

  @Test
  @DisplayName("Cambiar la contraseña exige la actual correcta y una nueva distinta")
  void changePasswordValidations() throws Exception {
    Tenant cafe = newBusiness();

    post(
        cafe.token(),
        "/auth/change-password",
        Map.of("currentPassword", "no-es-esta", "newPassword", NEW_PASSWORD),
        400);
    post(
        cafe.token(),
        "/auth/change-password",
        Map.of("currentPassword", PASSWORD, "newPassword", PASSWORD),
        400);
    post(cafe.token(), "/auth/change-password", Map.of("currentPassword", PASSWORD, "newPassword", "corta"), 400);

    get(cafe.token(), "/auth/me", 200); // nada de eso le cortó la sesión
  }

  @Test
  @DisplayName("Dar de baja a un empleado lo saca al instante")
  void deactivationKicksOut() throws Exception {
    Tenant cafe = newBusiness();
    Staff mozo = newStaff(cafe, "WAITER");
    get(mozo.token(), "/orders", 200);

    delete(cafe.token(), "/users/" + mozo.userId(), 200);

    get(mozo.token(), "/orders", 401);
    post(null, "/auth/login", Map.of("email", mozo.email(), "password", PASSWORD), 401);
  }

  @Test
  @DisplayName("El dueño le pone una contraseña nueva a un empleado: la sesión vieja se corta")
  void ownerResetsStaffPassword() throws Exception {
    Tenant cafe = newBusiness();
    Staff mozo = newStaff(cafe, "WAITER");

    patch(cafe.token(), "/users/" + mozo.userId(), Map.of("password", NEW_PASSWORD), 200);

    get(mozo.token(), "/orders", 401);
    login(mozo.email(), NEW_PASSWORD);
  }

  @Test
  @DisplayName("Un ADMIN no puede tocar la contraseña del dueño, y la propia se cambia desde Mi cuenta")
  void adminLimits() throws Exception {
    Tenant cafe = newBusiness();
    Staff admin = newStaff(cafe, "ADMIN");

    patch(admin.token(), "/users/" + cafe.userId(), Map.of("password", NEW_PASSWORD), 403);
    delete(admin.token(), "/users/" + cafe.userId(), 403);
    patch(admin.token(), "/users/" + admin.userId(), Map.of("password", NEW_PASSWORD), 403);

    login(cafe.email(), PASSWORD); // el dueño sigue entrando con la suya
  }

  @Test
  @DisplayName("Un mozo no puede tocar la carta ni el equipo")
  void waiterPermissions() throws Exception {
    Tenant cafe = newBusiness();
    Staff mozo = newStaff(cafe, "WAITER");

    post(mozo.token(), "/categories", Map.of("name", "Nueva"), 403);
    get(mozo.token(), "/users", 403);
    get(mozo.token(), "/dashboard/summary?locationId=" + cafe.locationId() + "&from=2026-01-01T00:00:00Z&to=2026-12-31T00:00:00Z", 403);
  }

  @Test
  @DisplayName("Olvidé mi contraseña: llega el link, sirve una sola vez y corta las sesiones abiertas")
  void forgotAndResetPassword() throws Exception {
    Tenant cafe = newBusiness();

    post(null, "/auth/forgot-password", Map.of("email", cafe.email()), 204);
    String token = mails.lastResetTokenFor(cafe.email()).orElseThrow(() -> new AssertionError("No llegó el mail"));

    post(null, "/auth/reset-password", Map.of("token", token, "newPassword", NEW_PASSWORD), 204);

    get(cafe.token(), "/auth/me", 401);
    login(cafe.email(), NEW_PASSWORD);
    // El mismo link no sirve dos veces.
    post(null, "/auth/reset-password", Map.of("token", token, "newPassword", "otra-clave-9999"), 400);
    login(cafe.email(), NEW_PASSWORD);
  }

  @Test
  @DisplayName("Olvidé mi contraseña con un mail que no existe responde igual, sin mandar nada")
  void forgotUnknownEmail() throws Exception {
    String email = "nadie-" + unique() + "@test.local";

    post(null, "/auth/forgot-password", Map.of("email", email), 204);

    assertThat(mails.sentTo(email)).isEmpty();
  }

  @Test
  @DisplayName("Un link inventado no sirve")
  void invalidResetToken() throws Exception {
    post(null, "/auth/reset-password", Map.of("token", "inventado", "newPassword", NEW_PASSWORD), 400);
  }

  @Test
  @DisplayName("Pedir el link muchas veces seguidas se frena")
  void resetRateLimit() throws Exception {
    Tenant cafe = newBusiness();

    for (int i = 0; i < 3; i++) {
      post(null, "/auth/forgot-password", Map.of("email", cafe.email()), 204);
    }
    post(null, "/auth/forgot-password", Map.of("email", cafe.email()), 429);
  }
}
