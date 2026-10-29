package com.cofeapp.backend.ops;

import static org.assertj.core.api.Assertions.assertThat;

import com.cofeapp.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * Endpoint de salud del servidor (ver docs/SERVIDOR.md): Docker, el script
 * de actualización y el monitor externo le preguntan si el sistema está vivo.
 */
@DisplayName("Endpoint de salud")
class HealthEndpointTest extends IntegrationTest {

  @Test
  @DisplayName("Responde UP sin sesión y sin mostrar detalles internos")
  void healthIsPublicAndTerse() throws Exception {
    JsonNode health = call(HttpMethod.GET, "/actuator/health", null, null, 200);
    assertThat(health.get("status").asText()).isEqualTo("UP");
    assertThat(health.has("components")).as("no muestra base de datos, disco, etc.").isFalse();

    assertThat(call(HttpMethod.GET, "/actuator/health/liveness", null, null, 200).get("status").asText()).isEqualTo("UP");
    assertThat(call(HttpMethod.GET, "/actuator/health/readiness", null, null, 200).get("status").asText()).isEqualTo("UP");
  }

  @Test
  @DisplayName("No publica ningún otro endpoint de Actuator")
  void nothingElseIsExposed() throws Exception {
    Tenant cafe = newBusiness();
    for (String path : new String[] {"/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/metrics"}) {
      // Sin sesión: 401 (nunca 200). Con sesión de dueño: no existe (404).
      call(HttpMethod.GET, path, null, null, 401);
      call(HttpMethod.GET, path, cafe.token(), null, 404);
    }
  }
}
