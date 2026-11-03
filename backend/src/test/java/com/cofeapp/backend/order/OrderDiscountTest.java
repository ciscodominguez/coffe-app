package com.cofeapp.backend.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.cofeapp.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** Descuentos (V12): lista fija + a mano, por ítem o sobre todo el pedido. */
@DisplayName("Descuentos")
class OrderDiscountTest extends IntegrationTest {

  // ---------------------------------------------------------------------
  // Lista de descuentos
  // ---------------------------------------------------------------------

  @Test
  @DisplayName("El dueño arma la lista; el mozo la ve pero no la edita; no se repiten nombres ni pasan del 100%")
  void discountCatalog() throws Exception {
    Tenant cafe = newBusiness();
    Staff mozo = newStaff(cafe, "WAITER");

    JsonNode jubilados = post(cafe.token(), "/discounts", preset("Jubilados", "PERCENTAGE", "10", false), 201);
    assertThat(jubilados.get("isActive").asBoolean()).isTrue();
    post(cafe.token(), "/discounts", preset("jubilados", "FIXED_AMOUNT", "500", false), 409);
    post(cafe.token(), "/discounts", preset("Todo gratis y más", "PERCENTAGE", "150", false), 400);
    post(cafe.token(), "/discounts", preset("Cero", "FIXED_AMOUNT", "0", false), 400);

    assertThat(get(mozo.token(), "/discounts")).hasSize(1);
    post(mozo.token(), "/discounts", preset("Amigos", "PERCENTAGE", "50", false), 403);

    delete(cafe.token(), "/discounts/" + id(jubilados), 204);
    assertThat(get(cafe.token(), "/discounts").get(0).get("isActive").asBoolean()).isFalse();
  }

  // ---------------------------------------------------------------------
  // Aplicar
  // ---------------------------------------------------------------------

  @Test
  @DisplayName("10% sobre todo el pedido baja el total, y se recalcula al agregar productos")
  void percentageOnWholeOrder() throws Exception {
    Tenant cafe = newBusiness();
    Staff mozo = newStaff(cafe, "WAITER");
    String cafeConLeche = newProduct(cafe, "Café con leche", "2500");
    String jubilados = id(post(cafe.token(), "/discounts", preset("Jubilados", "PERCENTAGE", "10", false), 201));

    JsonNode order = createOrder(mozo.token(), cafe.locationId(), null, item(cafeConLeche, 2));
    JsonNode discounted = applyDiscount(mozo.token(), id(order), Map.of("discountId", jubilados), 200);

    assertMoney(discounted.get("subtotal"), "5000");
    assertMoney(discounted.get("discountTotal"), "500");
    assertMoney(discounted.get("total"), "4500");
    assertMoney(discounted.get("balanceDue"), "4500");
    JsonNode applied = discounted.get("discounts").get(0);
    assertThat(applied.get("name").asText()).isEqualTo("Jubilados");
    assertThat(applied.get("orderItemId").isNull()).isTrue();
    assertThat(applied.get("createdByName").asText()).isEqualTo("Empleado WAITER");

    JsonNode more = addItems(mozo.token(), id(order), 200, item(cafeConLeche, 1));
    assertMoney(more.get("discountTotal"), "750");
    assertMoney(more.get("total"), "6750");
  }

  @Test
  @DisplayName("Un monto fijo sobre un ítem baja esa línea y nunca descuenta más de lo que vale")
  void fixedOnItem() throws Exception {
    Tenant cafe = newBusiness();
    String medialunas = newProduct(cafe, "Medialunas", "900");
    String tostado = newProduct(cafe, "Tostado", "4000");
    String cortesia = id(post(cafe.token(), "/discounts", preset("Cortesía", "FIXED_AMOUNT", "5000", false), 201));
    String media = id(post(cafe.token(), "/discounts", preset("Media docena", "FIXED_AMOUNT", "500", false), 201));

    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(medialunas, 2), item(tostado, 1));
    // Los renglones salen en el orden en que se cargaron (V13).
    assertThat(order.get("items").get(0).get("productName").asText()).isEqualTo("Medialunas");
    String medialunasItem = itemNamed(order, "Medialunas").get("id").asText();

    JsonNode withMedia = applyDiscount(cafe.token(), id(order), Map.of("discountId", media, "orderItemId", medialunasItem), 200);
    JsonNode line = itemNamed(withMedia, "Medialunas");
    assertMoney(line.get("discountAmount"), "500");
    assertMoney(line.get("subtotal"), "1300");
    assertMoney(withMedia.get("total"), "5300");

    // Aplicar otro al mismo ítem lo reemplaza; y $5000 sobre una línea de $1800 descuenta $1800.
    JsonNode withCourtesy =
        applyDiscount(cafe.token(), id(order), Map.of("discountId", cortesia, "orderItemId", medialunasItem), 200);
    assertThat(withCourtesy.get("discounts")).hasSize(1);
    assertMoney(itemNamed(withCourtesy, "Medialunas").get("discountAmount"), "1800");
    assertMoney(itemNamed(withCourtesy, "Medialunas").get("subtotal"), "0");
    assertMoney(withCourtesy.get("total"), "4000");

    // Quitarlo vuelve todo a como estaba.
    String appliedId = withCourtesy.get("discounts").get(0).get("id").asText();
    JsonNode removed = delete(cafe.token(), "/orders/" + id(order) + "/discounts/" + appliedId, 200);
    assertThat(removed.get("discounts")).isEmpty();
    assertMoney(itemNamed(removed, "Medialunas").get("subtotal"), "1800");
    assertMoney(removed.get("total"), "5800");
  }

  @Test
  @DisplayName("Ítem y pedido a la vez: el porcentaje del pedido se calcula sobre lo que queda de los ítems")
  void itemAndOrderTogether() throws Exception {
    Tenant cafe = newBusiness();
    String latte = newProduct(cafe, "Latte", "3000");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(latte, 2));
    String itemId = order.get("items").get(0).get("id").asText();

    applyDiscount(cafe.token(), id(order), manual("FIXED_AMOUNT", "1000", "Se enfrió", itemId), 200);
    JsonNode both = applyDiscount(cafe.token(), id(order), manual("PERCENTAGE", "10", "Cliente frecuente", null), 200);

    assertMoney(both.get("subtotal"), "5000"); // 6000 − 1000
    assertMoney(both.get("discountTotal"), "500"); // 10% de 5000
    assertMoney(both.get("total"), "4500");
    assertThat(both.get("discounts")).hasSize(2);
  }

  // ---------------------------------------------------------------------
  // Permisos (decisión de Fran: mozo la lista, jefe libre)
  // ---------------------------------------------------------------------

  @Test
  @DisplayName("El mozo usa la lista, pero no los que piden autorización ni los a mano; el supervisor sí")
  void permissions() throws Exception {
    Tenant cafe = newBusiness();
    Staff mozo = newStaff(cafe, "WAITER");
    Staff supervisor = newStaff(cafe, "SUPERVISOR");
    Staff cocina = newStaff(cafe, "KITCHEN");
    String latte = newProduct(cafe, "Latte", "3000");
    String empleado = id(post(cafe.token(), "/discounts", preset("Empleado", "PERCENTAGE", "50", true), 201));
    JsonNode order = createOrder(mozo.token(), cafe.locationId(), null, item(latte, 1));

    JsonNode denied = applyDiscount(mozo.token(), id(order), Map.of("discountId", empleado), 403);
    assertThat(denied.get("message").asText()).contains("supervisor");
    applyDiscount(mozo.token(), id(order), manual("PERCENTAGE", "20", "Amigo", null), 403);
    applyDiscount(cocina.token(), id(order), manual("PERCENTAGE", "20", "Amigo", null), 403);

    JsonNode bySupervisor = applyDiscount(supervisor.token(), id(order), Map.of("discountId", empleado), 200);
    assertMoney(bySupervisor.get("total"), "1500");
    String appliedId = bySupervisor.get("discounts").get(0).get("id").asText();

    // El mozo no puede quitarlo ni pisarlo con otro.
    delete(mozo.token(), "/orders/" + id(order) + "/discounts/" + appliedId, 403);
    String comun = id(post(cafe.token(), "/discounts", preset("Promo", "PERCENTAGE", "5", false), 201));
    applyDiscount(mozo.token(), id(order), Map.of("discountId", comun), 403);
    delete(supervisor.token(), "/orders/" + id(order) + "/discounts/" + appliedId, 200);
  }

  @Test
  @DisplayName("A mano: el motivo es obligatorio y queda registrado; el porcentaje no pasa del 100%")
  void manualDiscount() throws Exception {
    Tenant cafe = newBusiness();
    String latte = newProduct(cafe, "Latte", "3000");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(latte, 1));

    JsonNode noReason = applyDiscount(cafe.token(), id(order), manual("PERCENTAGE", "10", " ", null), 400);
    assertThat(noReason.get("message").asText()).contains("motivo");
    applyDiscount(cafe.token(), id(order), manual("PERCENTAGE", "120", "Error", null), 400);
    applyDiscount(cafe.token(), id(order), Map.of(), 400);

    JsonNode ok = applyDiscount(cafe.token(), id(order), manual("PERCENTAGE", "15", "Demora en la cocina", null), 200);
    JsonNode applied = ok.get("discounts").get(0);
    assertThat(applied.get("name").asText()).isEqualTo("Descuento 15%");
    assertThat(applied.get("reason").asText()).isEqualTo("Demora en la cocina");
    assertThat(applied.get("supervisorOnly").asBoolean()).isTrue();
    assertMoney(ok.get("total"), "2550");
  }

  // ---------------------------------------------------------------------
  // Reglas con el cobro
  // ---------------------------------------------------------------------

  @Test
  @DisplayName("No deja el total por debajo de lo cobrado; con el descuento el pedido se entrega cobrando menos")
  void paymentsAndDiscounts() throws Exception {
    Tenant cafe = newBusiness();
    Staff cocina = newStaff(cafe, "KITCHEN");
    String latte = newProduct(cafe, "Latte", "3000");
    openCash(cafe.token(), cafe.locationId(), "0");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(latte, 2));

    pay(cafe.token(), id(order), "CASH", "5000", 201);
    JsonNode tooMuch = applyDiscount(cafe.token(), id(order), manual("PERCENTAGE", "50", "Prueba", null), 409);
    assertThat(tooMuch.get("message").asText()).contains("Ya se cobraron");

    JsonNode ok = applyDiscount(cafe.token(), id(order), manual("FIXED_AMOUNT", "1000", "Cumpleaños", null), 200);
    assertMoney(ok.get("total"), "5000");
    assertMoney(ok.get("balanceDue"), "0");

    setStatus(cocina.token(), id(order), "IN_PREPARATION", 200);
    setStatus(cocina.token(), id(order), "READY", 200);
    setStatus(cafe.token(), id(order), "DELIVERED", 200);

    // Cerrado: ya no se tocan los descuentos.
    applyDiscount(cafe.token(), id(order), manual("FIXED_AMOUNT", "1", "Tarde", null), 409);
  }

  @Test
  @DisplayName("Un descuento desactivado ya no se puede aplicar; el aplicado antes queda como estaba")
  void deactivatedDiscount() throws Exception {
    Tenant cafe = newBusiness();
    String latte = newProduct(cafe, "Latte", "3000");
    String promo = id(post(cafe.token(), "/discounts", preset("Promo", "PERCENTAGE", "10", false), 201));
    JsonNode first = createOrder(cafe.token(), cafe.locationId(), null, item(latte, 1));
    applyDiscount(cafe.token(), id(first), Map.of("discountId", promo), 200);

    Map<String, Object> changed = preset("Promo", "PERCENTAGE", "50", false);
    call(HttpMethod.PUT, "/discounts/" + promo, cafe.token(), changed, 200);
    delete(cafe.token(), "/discounts/" + promo, 204);

    JsonNode second = createOrder(cafe.token(), cafe.locationId(), null, item(latte, 1));
    applyDiscount(cafe.token(), id(second), Map.of("discountId", promo), 409);

    JsonNode stillFirst = get(cafe.token(), "/orders/" + id(first));
    assertMoney(stillFirst.get("total"), "2700"); // sigue el 10% que se aplicó
  }

  @Test
  @DisplayName("Otro negocio no puede usar descuentos ni tocar pedidos ajenos")
  void tenantIsolation() throws Exception {
    Tenant cafe = newBusiness();
    Tenant otro = newBusiness();
    String latte = newProduct(cafe, "Latte", "3000");
    String suyo = newProduct(otro, "Latte", "3000");
    String promo = id(post(cafe.token(), "/discounts", preset("Promo", "PERCENTAGE", "10", false), 201));
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(latte, 1));
    JsonNode theirOrder = createOrder(otro.token(), otro.locationId(), null, item(suyo, 1));

    assertThat(get(otro.token(), "/discounts")).isEmpty();
    call(HttpMethod.PUT, "/discounts/" + promo, otro.token(), preset("X", "PERCENTAGE", "99", false), 404);
    applyDiscount(otro.token(), id(order), manual("PERCENTAGE", "10", "x", null), 404);
    applyDiscount(otro.token(), id(theirOrder), Map.of("discountId", promo), 404);

    String cafeItem = order.get("items").get(0).get("id").asText();
    applyDiscount(otro.token(), id(theirOrder), manual("PERCENTAGE", "10", "x", cafeItem), 404);
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  private static Map<String, Object> preset(String name, String type, String value, boolean requiresAuthorization) {
    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("type", type);
    body.put("value", new BigDecimal(value));
    body.put("requiresAuthorization", requiresAuthorization);
    return body;
  }

  private static Map<String, Object> manual(String type, String value, String reason, String orderItemId) {
    Map<String, Object> body = new HashMap<>();
    body.put("type", type);
    body.put("value", new BigDecimal(value));
    body.put("reason", reason);
    if (orderItemId != null) body.put("orderItemId", orderItemId);
    return body;
  }

  private static JsonNode itemNamed(JsonNode order, String productName) {
    for (JsonNode item : order.get("items")) {
      if (item.get("productName").asText().equals(productName)) return item;
    }
    throw new AssertionError("No hay " + productName + " en el pedido");
  }

  private JsonNode applyDiscount(String token, String orderId, Map<String, Object> body, int expectedStatus) throws Exception {
    return post(token, "/orders/" + orderId + "/discounts", body, expectedStatus);
  }
}
