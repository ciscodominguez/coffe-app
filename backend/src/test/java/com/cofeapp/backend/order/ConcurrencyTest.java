package com.cofeapp.backend.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.cofeapp.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Cosas que pasan a la vez en un café de verdad (prueba de carga, oct.
 * 2026). Cada caso larga varias requests exactamente al mismo tiempo y
 * revisa que los números cierren.
 */
@DisplayName("Operaciones simultáneas")
class ConcurrencyTest extends IntegrationTest {

  private record Res(int status, JsonNode body) {}

  @Test
  @DisplayName("Dos cobros a la vez del total del mismo pedido: se cobra una sola vez")
  void doublePayment() throws Exception {
    Tenant cafe = newBusiness();
    String cafeConLeche = newProduct(cafe, "Café con leche", "2500");
    openCash(cafe.token(), cafe.locationId(), "0");
    for (int round = 0; round < 3; round++) {
      String order = id(createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 1)));
      List<Res> rs = all(2, i -> send("POST", "/orders/" + order + "/payments", cafe.token(), Map.of("paymentMethod", "CASH", "amount", 2500)));
      assertThat(rs).extracting(Res::status).containsExactlyInAnyOrder(201, 409);
      assertMoney(get(cafe.token(), "/orders/" + order).get("paidTotal"), "2500");
    }
  }

  @Test
  @DisplayName("Cuatro mozos eligen la misma mesa a la vez: un solo pedido")
  void sameTable() throws Exception {
    Tenant cafe = newBusiness();
    String cafeConLeche = newProduct(cafe, "Café con leche", "2500");
    for (int n = 1; n <= 3; n++) {
      String mesa = id(post(cafe.token(), "/tables", Map.of("locationId", cafe.locationId(), "number", n), 201));
      List<Res> rs = all(4, i -> send("POST", "/orders", cafe.token(), orderBody(cafe, mesa, UUID.randomUUID().toString(), cafeConLeche, 1)));
      assertThat(rs.stream().filter(r -> r.status() == 201)).hasSize(1);
      assertThat(rs).extracting(Res::status).allMatch(s -> s == 201 || s == 409);
    }
  }

  @Test
  @DisplayName("Doble clic en Enviar (misma clave, 5 a la vez): un solo pedido y todos reciben ese")
  void doubleSubmit() throws Exception {
    Tenant cafe = newBusiness();
    String cafeConLeche = newProduct(cafe, "Café con leche", "2500");
    String mesa = id(post(cafe.token(), "/tables", Map.of("locationId", cafe.locationId(), "number", 1), 201));
    for (String table : new String[] {null, mesa}) {
      String key = UUID.randomUUID().toString();
      List<Res> rs = all(5, i -> send("POST", "/orders", cafe.token(), orderBody(cafe, table, key, cafeConLeche, 1)));
      assertThat(rs).extracting(Res::status).containsOnly(201);
      assertThat(rs.stream().map(r -> id(r.body())).distinct()).hasSize(1);
    }
  }

  @Test
  @DisplayName("Seis mozos agregan al mismo pedido a la vez: el total es la suma de los productos")
  void concurrentAdditions() throws Exception {
    Tenant cafe = newBusiness();
    String cafeConLeche = newProduct(cafe, "Café con leche", "2500");
    String medialuna = newProduct(cafe, "Medialuna", "900");
    String order = id(createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 1)));
    all(6, i -> send("POST", "/orders/" + order + "/items", cafe.token(), Map.of("items", List.of(item(medialuna, 1)))));
    JsonNode after = get(cafe.token(), "/orders/" + order);
    assertThat(after.get("items")).hasSize(7);
    assertMoney(after.get("total"), String.valueOf(2500 + 6 * 900));
  }

  @Test
  @DisplayName("Stock: 20 ventas a la vez del mismo producto descuentan exacto, y 30 con dos productos en distinto orden no se traban")
  void stock() throws Exception {
    Tenant cafe = newBusiness();
    String latte = newProduct(cafe, "Latte", "3500");
    String tostado = newProduct(cafe, "Tostado", "4500");
    for (String p : List.of(latte, tostado)) {
      patch(cafe.token(), "/stock/products/" + p + "/config", Map.of("stockMode", "DIRECT", "minStock", 0), 200);
      post(cafe.token(), "/stock/products/" + p + "/adjust", Map.of("locationId", cafe.locationId(), "newQuantity", 100), 200);
    }
    List<Res> rs = all(20, i -> send("POST", "/orders", cafe.token(), orderBody(cafe, null, UUID.randomUUID().toString(), latte, 1)));
    assertThat(rs).extracting(Res::status).containsOnly(201);
    assertThat(stockOf(cafe, latte)).isEqualByComparingTo("80");

    rs =
        all(
            30,
            i -> {
              Map<String, Object> body = orderBody(cafe, null, UUID.randomUUID().toString(), latte, 1);
              List<Map<String, Object>> items = new ArrayList<>(List.of(item(latte, 1), item(tostado, 1)));
              if (i % 2 == 1) java.util.Collections.reverse(items);
              body.put("items", items);
              return send("POST", "/orders", cafe.token(), body);
            });
    assertThat(rs).extracting(Res::status).containsOnly(201);
    assertThat(stockOf(cafe, latte)).isEqualByComparingTo("50");
    assertThat(stockOf(cafe, tostado)).isEqualByComparingTo("70");

    // Cancelar 10 a la vez: vuelve todo.
    List<String> ids = rs.stream().limit(10).map(r -> id(r.body())).toList();
    List<Res> cancelled = all(10, i -> send("PATCH", "/orders/" + ids.get(i) + "/status", cafe.token(), Map.of("status", "CANCELLED")));
    assertThat(cancelled).extracting(Res::status).containsOnly(200);
    assertThat(stockOf(cafe, latte)).isEqualByComparingTo("60");
    assertThat(stockOf(cafe, tostado)).isEqualByComparingTo("80");
  }

  @Test
  @DisplayName("Un pedido cobrado no se puede cancelar (la plata quedaría en la caja de un pedido anulado); uno cobrado por la app sí")
  void cancelCharged() throws Exception {
    Tenant cafe = newBusiness();
    String cafeConLeche = newProduct(cafe, "Café con leche", "2500");
    openCash(cafe.token(), cafe.locationId(), "0");
    String order = id(createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 1)));
    pay(cafe.token(), order, "CASH", "1000", 201);
    JsonNode refused = setStatus(cafe.token(), order, "CANCELLED", 409);
    assertThat(refused.get("message").asText()).contains("ya tiene cobros");

    String pya = id(post(cafe.token(), "/sales-channels", Map.of("locationId", cafe.locationId(), "kind", "PEDIDOSYA", "mode", "MANUAL"), 201));
    Map<String, Object> body = orderBody(cafe, null, UUID.randomUUID().toString(), cafeConLeche, 1);
    body.put("channel", Map.of("channelId", pya, "orderRef", "77", "paidByApp", true));
    String appOrder = id(post(cafe.token(), "/orders", body, 201));
    setStatus(cafe.token(), appOrder, "CANCELLED", 200);
  }

  @Test
  @DisplayName("Tres descuentos 'a todo el pedido' a la vez: queda uno solo")
  void concurrentDiscounts() throws Exception {
    Tenant cafe = newBusiness();
    String tostado = newProduct(cafe, "Tostado", "4500");
    String order = id(createOrder(cafe.token(), cafe.locationId(), null, item(tostado, 2)));
    all(3, i -> send("POST", "/orders/" + order + "/discounts", cafe.token(), Map.of("type", "PERCENTAGE", "value", 10, "reason", "prueba")));
    JsonNode after = get(cafe.token(), "/orders/" + order);
    assertThat(after.get("discounts")).hasSize(1);
    assertMoney(after.get("total"), "8100");
  }

  // ---------------------------------------------------------------------

  private BigDecimal stockOf(Tenant cafe, String productId) throws Exception {
    for (JsonNode level : get(cafe.token(), "/stock/products/levels?locationId=" + cafe.locationId())) {
      if (level.get("productId").asText().equals(productId)) return new BigDecimal(level.get("currentStock").asText());
    }
    throw new AssertionError("sin stock para " + productId);
  }

  private Map<String, Object> orderBody(Tenant cafe, String tableId, String key, String productId, int qty) {
    Map<String, Object> body = new HashMap<>();
    body.put("locationId", cafe.locationId());
    if (tableId != null) body.put("tableId", tableId);
    body.put("idempotencyKey", key);
    body.put("clientTimestamp", Instant.now().toString());
    body.put("items", new ArrayList<>(List.of(item(productId, qty))));
    return body;
  }

  private interface Indexed {
    Res call(int i) throws Exception;
  }

  /** Larga n requests exactamente a la vez (todas esperan la misma largada). */
  private List<Res> all(int n, Indexed request) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(n);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Res>> futures = new ArrayList<>();
      for (int i = 0; i < n; i++) {
        int index = i;
        Callable<Res> task =
            () -> {
              start.await();
              return request.call(index);
            };
        futures.add(pool.submit(task));
      }
      start.countDown();
      List<Res> out = new ArrayList<>();
      for (Future<Res> f : futures) out.add(f.get());
      assertThat(out).as("ningún error interno").allMatch(r -> r.status() < 500);
      return out;
    } finally {
      pool.shutdownNow();
    }
  }

  private Res send(String method, String url, String token, Object body) throws Exception {
    MockHttpServletRequestBuilder req =
        MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method), url)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body));
    MvcResult result = mvc.perform(req).andReturn();
    String content = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    return new Res(result.getResponse().getStatus(), content.isBlank() ? null : json.readTree(content));
  }
}
