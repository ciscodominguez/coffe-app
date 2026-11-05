package com.cofeapp.backend.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.cofeapp.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Dividir la cuenta (V14): en partes iguales y por lo que consumió cada uno. */
@DisplayName("Dividir la cuenta")
class OrderSplitTest extends IntegrationTest {

  @Test
  @DisplayName("Por productos: cada uno paga lo suyo, quedan marcados, y el último paga justo el saldo")
  void payByItems() throws Exception {
    Tenant cafe = newBusiness();
    String latte = newProduct(cafe, "Latte", "1500");
    String tostado = newProduct(cafe, "Tostado", "4000");
    openCash(cafe.token(), cafe.locationId(), "0");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(latte, 2), item(tostado, 1));
    String latteLine = itemNamed(order, "Latte").get("id").asText();
    String tostadoLine = itemNamed(order, "Tostado").get("id").asText();

    JsonNode first = payItems(cafe.token(), id(order), "CASH", List.of(line(latteLine, 1)), 201);
    assertMoney(first.get("paidTotal"), "1500");
    assertMoney(first.get("balanceDue"), "5500");
    assertThat(itemNamed(first, "Latte").get("paidQuantity").asInt()).isEqualTo(1);
    JsonNode payment = first.get("payments").get(0);
    assertMoney(payment.get("amount"), "1500");
    assertThat(payment.get("items").get(0).get("orderItemId").asText()).isEqualTo(latteLine);
    assertThat(payment.get("items").get(0).get("quantity").asInt()).isEqualTo(1);

    JsonNode second = payItems(cafe.token(), id(order), "DEBIT_CARD", List.of(line(latteLine, 1), line(tostadoLine, 1)), 201);
    assertMoney(second.get("balanceDue"), "0");
    assertThat(itemNamed(second, "Latte").get("paidQuantity").asInt()).isEqualTo(2);
    assertThat(itemNamed(second, "Tostado").get("paidQuantity").asInt()).isEqualTo(1);
    assertMoney(second.get("paidTotal"), "7000"); // 1500 + 5500
  }

  @Test
  @DisplayName("No deja cobrar dos veces lo mismo ni productos de otro pedido")
  void noDoublePayment() throws Exception {
    Tenant cafe = newBusiness();
    String latte = newProduct(cafe, "Latte", "1500");
    openCash(cafe.token(), cafe.locationId(), "0");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(latte, 2));
    JsonNode other = createOrder(cafe.token(), cafe.locationId(), null, item(latte, 1));
    String latteLine = itemNamed(order, "Latte").get("id").asText();

    JsonNode tooMany = payItems(cafe.token(), id(order), "CASH", List.of(line(latteLine, 3)), 409);
    assertThat(tooMany.get("message").asText()).contains("quedan 2");
    payItems(cafe.token(), id(order), "CASH", List.of(line(itemNamed(other, "Latte").get("id").asText(), 1)), 404);

    payItems(cafe.token(), id(order), "CASH", List.of(line(latteLine, 2)), 201);
    JsonNode again = payItems(cafe.token(), id(order), "CASH", List.of(line(latteLine, 1)), 409);
    assertThat(again.get("message").asText()).contains("ya está pagado");

    // Sin monto ni productos, no hay nada que cobrar.
    post(cafe.token(), "/orders/" + id(other) + "/payments", Map.of("paymentMethod", "CASH"), 400);
  }

  @Test
  @DisplayName("Con descuento sobre todo el pedido, cada uno paga su parte con el descuento repartido")
  void withOrderDiscount() throws Exception {
    Tenant cafe = newBusiness();
    String latte = newProduct(cafe, "Latte", "1500");
    String tostado = newProduct(cafe, "Tostado", "4000");
    openCash(cafe.token(), cafe.locationId(), "0");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(latte, 2), item(tostado, 1));
    Map<String, Object> tenPercent = new HashMap<>();
    tenPercent.put("type", "PERCENTAGE");
    tenPercent.put("value", new BigDecimal("10"));
    tenPercent.put("reason", "Cliente frecuente");
    JsonNode discounted = post(cafe.token(), "/orders/" + id(order) + "/discounts", tenPercent, 200);
    assertMoney(discounted.get("total"), "6300");

    JsonNode first = payItems(cafe.token(), id(order), "CASH", List.of(line(itemNamed(order, "Latte").get("id").asText(), 1)), 201);
    assertMoney(first.get("paidTotal"), "1350");
    JsonNode rest =
        payItems(
            cafe.token(),
            id(order),
            "CASH",
            List.of(line(itemNamed(order, "Latte").get("id").asText(), 1), line(itemNamed(order, "Tostado").get("id").asText(), 1)),
            201);
    assertMoney(rest.get("balanceDue"), "0");
    assertMoney(rest.get("paidTotal"), "6300");
  }

  @Test
  @DisplayName("Los centavos del redondeo nunca quedan colgados: el último paga justo lo que falta")
  void rounding() throws Exception {
    Tenant cafe = newBusiness();
    String a = newProduct(cafe, "Café A", "1000");
    String b = newProduct(cafe, "Café B", "1000");
    String c = newProduct(cafe, "Café C", "1000");
    openCash(cafe.token(), cafe.locationId(), "0");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(a, 1), item(b, 1), item(c, 1));
    Map<String, Object> hundred = new HashMap<>();
    hundred.put("type", "FIXED_AMOUNT");
    hundred.put("value", new BigDecimal("100"));
    hundred.put("reason", "Demora");
    post(cafe.token(), "/orders/" + id(order) + "/discounts", hundred, 200); // total 2900

    JsonNode one = payItems(cafe.token(), id(order), "CASH", List.of(line(itemNamed(order, "Café A").get("id").asText(), 1)), 201);
    assertMoney(one.get("paidTotal"), "966.67");
    JsonNode two = payItems(cafe.token(), id(order), "CASH", List.of(line(itemNamed(order, "Café B").get("id").asText(), 1)), 201);
    assertMoney(two.get("paidTotal"), "1933.34");
    JsonNode three = payItems(cafe.token(), id(order), "CASH", List.of(line(itemNamed(order, "Café C").get("id").asText(), 1)), 201);
    assertMoney(three.get("paidTotal"), "2900"); // el último pagó 966,66
    assertMoney(three.get("balanceDue"), "0");
  }

  @Test
  @DisplayName("Mezclado con cobros comunes: por productos nunca cobra más que el saldo")
  void mixedWithRegularPayments() throws Exception {
    Tenant cafe = newBusiness();
    String latte = newProduct(cafe, "Latte", "1000");
    openCash(cafe.token(), cafe.locationId(), "0");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(latte, 3));
    pay(cafe.token(), id(order), "CASH", "2500", 201);

    JsonNode capped = payItems(cafe.token(), id(order), "CASH", List.of(line(itemNamed(order, "Latte").get("id").asText(), 1)), 201);
    assertMoney(capped.get("paidTotal"), "3000"); // pagó 500, no 1000
    assertMoney(capped.get("balanceDue"), "0");
  }

  @Test
  @DisplayName("Partes iguales son cobros comunes; lo agregado después aparece sin pagar")
  void equalPartsAndAddedItems() throws Exception {
    Tenant cafe = newBusiness();
    String latte = newProduct(cafe, "Latte", "1000");
    String tostado = newProduct(cafe, "Tostado", "3000");
    openCash(cafe.token(), cafe.locationId(), "0");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(latte, 3));

    pay(cafe.token(), id(order), "CASH", "1000", 201);
    pay(cafe.token(), id(order), "DEBIT_CARD", "1000", 201);
    JsonNode split = pay(cafe.token(), id(order), "TRANSFER", "1000", 201);
    assertMoney(split.get("balanceDue"), "0");
    assertThat(itemNamed(split, "Latte").get("paidQuantity").asInt()).isZero();

    JsonNode more = addItems(cafe.token(), id(order), 200, item(tostado, 1));
    assertMoney(more.get("balanceDue"), "3000");
    assertThat(itemNamed(more, "Tostado").get("paidQuantity").asInt()).isZero();
  }

  // ---------------------------------------------------------------------

  private static Map<String, Object> line(String orderItemId, int quantity) {
    return Map.of("orderItemId", orderItemId, "quantity", quantity);
  }

  private JsonNode payItems(String token, String orderId, String method, List<Map<String, Object>> items, int expectedStatus)
      throws Exception {
    return post(token, "/orders/" + orderId + "/payments", Map.of("paymentMethod", method, "items", items), expectedStatus);
  }

  private static JsonNode itemNamed(JsonNode order, String productName) {
    for (JsonNode item : order.get("items")) {
      if (item.get("productName").asText().equals(productName)) return item;
    }
    throw new AssertionError("No hay " + productName + " en el pedido");
  }
}
