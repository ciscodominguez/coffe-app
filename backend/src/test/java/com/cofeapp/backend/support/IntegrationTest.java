package com.cofeapp.backend.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Base de los tests de integración: levanta la aplicación entera (seguridad
 * JWT, filtro multi-tenant, servicios, MySQL de test) y le pega a la API
 * igual que el frontend, con {@link MockMvc}.
 *
 * <p>Cada test se arma sus propios datos con un negocio nuevo ({@link
 * #newBusiness()}): emails y CUIT al azar, así ningún test depende de otro
 * ni del orden en que corren.
 *
 * <p>Las rutas van sin el prefijo {@code /api/v1}: MockMvc le habla
 * directo a la aplicación, sin pasar por el context-path del servidor.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
public abstract class IntegrationTest {

  protected static final String PASSWORD = "clave-de-prueba-1";

  @Autowired protected MockMvc mvc;
  @Autowired protected ObjectMapper json;
  @Autowired protected RecordingMailService mails;

  /** Un negocio recién registrado: el token de su dueño (OWNER) y su local por defecto. */
  protected record Tenant(String token, String userId, String businessId, String locationId, String email) {}

  /** Un empleado creado desde Equipo, ya logueado. */
  protected record Staff(String token, String userId, String email) {}

  // ---------------------------------------------------------------------
  // HTTP
  // ---------------------------------------------------------------------

  /** Hace la request y exige ese status. Devuelve el body como JSON (NullNode si vino vacío). */
  protected JsonNode call(HttpMethod method, String url, String token, Object body, int expectedStatus)
      throws Exception {
    MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(method, url).accept(MediaType.APPLICATION_JSON);
    if (token != null) {
      request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
    if (body != null) {
      request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }
    MvcResult result = mvc.perform(request).andReturn();
    String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertThat(result.getResponse().getStatus())
        .as("%s %s respondió: %s", method, url, content)
        .isEqualTo(expectedStatus);
    return content.isBlank() ? NullNode.getInstance() : json.readTree(content);
  }

  protected JsonNode get(String token, String url) throws Exception {
    return call(HttpMethod.GET, url, token, null, 200);
  }

  protected JsonNode get(String token, String url, int expectedStatus) throws Exception {
    return call(HttpMethod.GET, url, token, null, expectedStatus);
  }

  protected JsonNode post(String token, String url, Object body, int expectedStatus) throws Exception {
    return call(HttpMethod.POST, url, token, body, expectedStatus);
  }

  protected JsonNode patch(String token, String url, Object body, int expectedStatus) throws Exception {
    return call(HttpMethod.PATCH, url, token, body, expectedStatus);
  }

  protected JsonNode delete(String token, String url, int expectedStatus) throws Exception {
    return call(HttpMethod.DELETE, url, token, null, expectedStatus);
  }

  // ---------------------------------------------------------------------
  // Cuentas
  // ---------------------------------------------------------------------

  protected Tenant newBusiness() throws Exception {
    String suffix = unique();
    String email = "duenio-" + suffix + "@test.local";
    Map<String, Object> body = new HashMap<>();
    body.put("legalName", "Café de prueba " + suffix);
    body.put("taxId", randomCuit());
    body.put("fiscalCondition", "MONOTRIBUTO");
    body.put("ownerEmail", email);
    body.put("ownerPassword", PASSWORD);
    body.put("ownerFirstName", "Dueño");
    body.put("ownerLastName", "Prueba");
    body.put("acceptTerms", true);
    JsonNode auth = post(null, "/auth/register-business", body, 201);

    String token = auth.get("accessToken").asText();
    JsonNode user = auth.get("user");
    JsonNode locations = get(token, "/locations");
    assertThat(locations).hasSize(1);
    return new Tenant(
        token, user.get("id").asText(), user.get("businessId").asText(), locations.get(0).get("id").asText(), email);
  }

  /** Crea un empleado con ese rol (lo crea el dueño) y lo loguea. */
  protected Staff newStaff(Tenant tenant, String role) throws Exception {
    String email = role.toLowerCase() + "-" + unique() + "@test.local";
    Map<String, Object> body = new HashMap<>();
    body.put("email", email);
    body.put("password", PASSWORD);
    body.put("firstName", "Empleado");
    body.put("lastName", role);
    body.put("role", role);
    body.put("locationId", tenant.locationId());
    JsonNode user = post(tenant.token(), "/users", body, 201);
    return new Staff(login(email, PASSWORD), user.get("id").asText(), email);
  }

  protected String login(String email, String password) throws Exception {
    return post(null, "/auth/login", Map.of("email", email, "password", password), 200).get("accessToken").asText();
  }

  // ---------------------------------------------------------------------
  // Catálogo
  // ---------------------------------------------------------------------

  /** Crea una categoría y un producto adentro. Devuelve el id del producto. */
  protected String newProduct(Tenant tenant, String name, String price) throws Exception {
    String categoryId = post(tenant.token(), "/categories", Map.of("name", "Categoría " + unique()), 201).get("id").asText();
    Map<String, Object> body = Map.of("categoryId", categoryId, "name", name, "price", new BigDecimal(price));
    return post(tenant.token(), "/products", body, 201).get("id").asText();
  }

  // ---------------------------------------------------------------------
  // Pedidos, cobros y caja
  // ---------------------------------------------------------------------

  protected static Map<String, Object> item(String productId, int quantity) {
    return Map.of("productId", productId, "quantity", quantity);
  }

  @SafeVarargs
  protected final JsonNode createOrder(String token, String locationId, String tableId, Map<String, Object>... items)
      throws Exception {
    return createOrderWithKey(token, locationId, tableId, UUID.randomUUID().toString(), 201, items);
  }

  @SafeVarargs
  protected final JsonNode createOrderWithKey(
      String token, String locationId, String tableId, String idempotencyKey, int expectedStatus, Map<String, Object>... items)
      throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("locationId", locationId);
    if (tableId != null) body.put("tableId", tableId);
    body.put("idempotencyKey", idempotencyKey);
    body.put("clientTimestamp", Instant.now().toString());
    body.put("items", new ArrayList<>(Arrays.asList(items)));
    return post(token, "/orders", body, expectedStatus);
  }

  @SafeVarargs
  protected final JsonNode addItems(String token, String orderId, int expectedStatus, Map<String, Object>... items)
      throws Exception {
    return post(token, "/orders/" + orderId + "/items", Map.of("items", List.of(items)), expectedStatus);
  }

  protected JsonNode pay(String token, String orderId, String method, String amount, int expectedStatus) throws Exception {
    Map<String, Object> body = Map.of("paymentMethod", method, "amount", new BigDecimal(amount));
    return post(token, "/orders/" + orderId + "/payments", body, expectedStatus);
  }

  protected JsonNode setStatus(String token, String orderId, String status, int expectedStatus) throws Exception {
    return patch(token, "/orders/" + orderId + "/status", Map.of("status", status), expectedStatus);
  }

  protected String openCash(String token, String locationId, String openingAmount) throws Exception {
    Map<String, Object> body = Map.of("locationId", locationId, "openingAmount", new BigDecimal(openingAmount));
    return post(token, "/cash-sessions/open", body, 201).get("id").asText();
  }

  // ---------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------

  protected static String id(JsonNode node) {
    return node.get("id").asText();
  }

  /** Compara montos sin importar la escala (1500 == 1500.00). */
  protected static void assertMoney(JsonNode node, String expected) {
    assertThat(node).as("monto esperado %s", expected).isNotNull();
    assertThat(new BigDecimal(node.asText())).isEqualByComparingTo(expected);
  }

  protected static List<String> ids(JsonNode array) {
    List<String> result = new ArrayList<>();
    array.forEach(node -> result.add(node.get("id").asText()));
    return result;
  }

  protected static String unique() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  /** 11 dígitos al azar: el backend valida el formato del CUIT, no el dígito verificador. */
  protected static String randomCuit() {
    StringBuilder cuit = new StringBuilder("30");
    for (int i = 0; i < 9; i++) {
      cuit.append(ThreadLocalRandom.current().nextInt(10));
    }
    return cuit.toString();
  }
}
