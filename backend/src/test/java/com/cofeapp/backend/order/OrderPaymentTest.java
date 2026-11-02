package com.cofeapp.backend.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.cofeapp.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pedidos y cobro: lo que no puede fallar en el mostrador. */
@DisplayName("Pedidos y cobro")
class OrderPaymentTest extends IntegrationTest {

  @Test
  @DisplayName("Sin caja abierta no se puede cobrar")
  void paymentNeedsOpenCash() throws Exception {
    Tenant cafe = newBusiness();
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 2));

    pay(cafe.token(), id(order), "CASH", "3000", 409);
  }

  @Test
  @DisplayName("El pedido se arma con el precio del producto y el total sale de los ítems")
  void orderTotals() throws Exception {
    Tenant cafe = newBusiness();
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    String medialuna = newProduct(cafe, "Medialuna", "650.50");

    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 2), item(medialuna, 3));

    assertThat(order.get("status").asText()).isEqualTo("PENDING");
    assertThat(order.get("items")).hasSize(2);
    assertMoney(order.get("total"), "4951.50"); // 2 × 1500 + 3 × 650,50
    assertMoney(order.get("balanceDue"), "4951.50");
  }

  @Test
  @DisplayName("Cobro dividido entre efectivo y tarjeta: el saldo baja con cada pago")
  void splitPayment() throws Exception {
    Tenant cafe = newBusiness();
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    openCash(cafe.token(), cafe.locationId(), "0");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 2));

    JsonNode afterCash = pay(cafe.token(), id(order), "CASH", "1000", 201);
    assertMoney(afterCash.get("paidTotal"), "1000");
    assertMoney(afterCash.get("balanceDue"), "2000");

    JsonNode afterCard = pay(cafe.token(), id(order), "DEBIT_CARD", "2000", 201);
    assertMoney(afterCard.get("paidTotal"), "3000");
    assertMoney(afterCard.get("balanceDue"), "0");
    assertThat(afterCard.get("payments")).hasSize(2);
  }

  @Test
  @DisplayName("No se puede cobrar más que el saldo pendiente")
  void noOverpayment() throws Exception {
    Tenant cafe = newBusiness();
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    openCash(cafe.token(), cafe.locationId(), "0");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 2));

    pay(cafe.token(), id(order), "CASH", "3000.01", 409);
    pay(cafe.token(), id(order), "CASH", "2500", 201);
    pay(cafe.token(), id(order), "CASH", "500.01", 409);
  }

  @Test
  @DisplayName("Un pedido no se puede entregar hasta que esté cobrado por completo")
  void deliverNeedsFullPayment() throws Exception {
    Tenant cafe = newBusiness();
    Staff cocina = newStaff(cafe, "KITCHEN");
    Staff mozo = newStaff(cafe, "WAITER");
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    openCash(cafe.token(), cafe.locationId(), "0");
    JsonNode order = createOrder(mozo.token(), cafe.locationId(), null, item(cafeConLeche, 2));

    setStatus(cocina.token(), id(order), "IN_PREPARATION", 200);
    setStatus(cocina.token(), id(order), "READY", 200);
    pay(mozo.token(), id(order), "CASH", "1000", 201);

    setStatus(mozo.token(), id(order), "DELIVERED", 409);

    pay(mozo.token(), id(order), "QR", "2000", 201);
    JsonNode delivered = setStatus(mozo.token(), id(order), "DELIVERED", 200);
    assertThat(delivered.get("status").asText()).isEqualTo("DELIVERED");
    assertThat(delivered.get("closedAt").isNull()).isFalse();
  }

  @Test
  @DisplayName("Un pedido cerrado no acepta más cobros ni productos")
  void closedOrderIsFrozen() throws Exception {
    Tenant cafe = newBusiness();
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    openCash(cafe.token(), cafe.locationId(), "0");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 1));

    setStatus(cafe.token(), id(order), "CANCELLED", 200);

    pay(cafe.token(), id(order), "CASH", "1500", 409);
    addItems(cafe.token(), id(order), 409, item(cafeConLeche, 1));
    setStatus(cafe.token(), id(order), "PENDING", 409);
  }

  @Test
  @DisplayName("Agregar productos a un pedido abierto sube el total y el saldo, y vuelve a Nuevo")
  void addItemsToOpenOrder() throws Exception {
    Tenant cafe = newBusiness();
    Staff cocina = newStaff(cafe, "KITCHEN");
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    String tostado = newProduct(cafe, "Tostado", "800");
    openCash(cafe.token(), cafe.locationId(), "0");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 1));
    pay(cafe.token(), id(order), "CASH", "1500", 201);
    setStatus(cocina.token(), id(order), "IN_PREPARATION", 200);

    JsonNode updated = addItems(cafe.token(), id(order), 200, item(tostado, 2));

    assertMoney(updated.get("total"), "3100");
    assertMoney(updated.get("balanceDue"), "1600");
    assertThat(updated.get("status").asText()).isEqualTo("PENDING");
    assertThat(updated.get("items")).hasSize(2);
    JsonNode added = updated.get("items").get(1);
    assertThat(added.get("productName").asText()).isEqualTo("Tostado");
    assertThat(added.get("batch").asInt()).isEqualTo(2);
    assertThat(updated.get("items").get(0).get("batch").asInt()).isEqualTo(1);
  }

  @Test
  @DisplayName("El precio queda congelado en el pedido aunque después cambie en la carta")
  void priceSnapshot() throws Exception {
    Tenant cafe = newBusiness();
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 1));

    patch(cafe.token(), "/products/" + cafeConLeche, Map.of("price", 2000), 200);

    JsonNode reread = get(cafe.token(), "/orders/" + id(order));
    assertMoney(reread.get("total"), "1500");
    assertMoney(reread.get("items").get(0).get("unitPrice"), "1500");
  }

  @Test
  @DisplayName("Reenviar el mismo pedido (cola offline) no lo duplica")
  void idempotentCreate() throws Exception {
    Tenant cafe = newBusiness();
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    String key = UUID.randomUUID().toString();

    JsonNode first = createOrderWithKey(cafe.token(), cafe.locationId(), null, key, 201, item(cafeConLeche, 1));
    JsonNode retry = createOrderWithKey(cafe.token(), cafe.locationId(), null, key, 201, item(cafeConLeche, 1));

    assertThat(id(retry)).isEqualTo(id(first));
    assertThat(get(cafe.token(), "/orders")).hasSize(1);
  }

  @Test
  @DisplayName("Una mesa tiene un solo pedido abierto; al cerrarlo se libera")
  void oneOpenOrderPerTable() throws Exception {
    Tenant cafe = newBusiness();
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    String mesa = id(post(cafe.token(), "/tables", Map.of("locationId", cafe.locationId(), "number", 1), 201));

    JsonNode first = createOrder(cafe.token(), cafe.locationId(), mesa, item(cafeConLeche, 1));
    assertThat(get(cafe.token(), "/tables/" + mesa).get("status").asText()).isEqualTo("OCCUPIED");

    createOrder409(cafe, mesa, cafeConLeche);

    setStatus(cafe.token(), id(first), "CANCELLED", 200);
    assertThat(get(cafe.token(), "/tables/" + mesa).get("status").asText()).isEqualTo("FREE");
    createOrder(cafe.token(), cafe.locationId(), mesa, item(cafeConLeche, 1));
  }

  private void createOrder409(Tenant cafe, String mesa, String productId) throws Exception {
    createOrderWithKey(cafe.token(), cafe.locationId(), mesa, UUID.randomUUID().toString(), 409, item(productId, 1));
  }

  @Test
  @DisplayName("Cada rol mueve solo los estados que le tocan")
  void statusMachineByRole() throws Exception {
    Tenant cafe = newBusiness();
    Staff cocina = newStaff(cafe, "KITCHEN");
    Staff mozo = newStaff(cafe, "WAITER");
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");

    // Cocina no toma pedidos.
    createOrderWithKey(cocina.token(), cafe.locationId(), null, UUID.randomUUID().toString(), 403, item(cafeConLeche, 1));

    JsonNode order = createOrder(mozo.token(), cafe.locationId(), null, item(cafeConLeche, 1));
    setStatus(mozo.token(), id(order), "IN_PREPARATION", 403); // eso lo hace Cocina
    setStatus(cocina.token(), id(order), "READY", 409); // no se saltea "En preparación"
    setStatus(cocina.token(), id(order), "IN_PREPARATION", 200);
    setStatus(cocina.token(), id(order), "CANCELLED", 403); // Cocina no cancela
    setStatus(mozo.token(), id(order), "CANCELLED", 200);
  }

  @Test
  @DisplayName("No se puede pedir un producto dado de baja")
  void inactiveProduct() throws Exception {
    Tenant cafe = newBusiness();
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    delete(cafe.token(), "/products/" + cafeConLeche, 204);

    createOrderWithKey(cafe.token(), cafe.locationId(), null, UUID.randomUUID().toString(), 409, item(cafeConLeche, 1));
  }
}
