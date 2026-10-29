package com.cofeapp.backend.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.cofeapp.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Aislamiento entre negocios: un café nunca ve ni toca nada de otro, ni
 * aunque conozca los ids. Es la promesa más importante de un sistema que
 * comparten varios clientes.
 *
 * <p>Lo esperado siempre es 404 ("no existe"), nunca 403: para el café B,
 * las cosas del café A directamente no existen.
 */
@DisplayName("Aislamiento entre negocios")
class TenantIsolationTest extends IntegrationTest {

  private Tenant cafeA;
  private Tenant cafeB;
  private String productoA;
  private String categoriaA;
  private String pedidoA;
  private String cajaA;
  private String mesaA;

  @BeforeEach
  void twoBusinesses() throws Exception {
    cafeA = newBusiness();
    cafeB = newBusiness();

    categoriaA = post(cafeA.token(), "/categories", Map.of("name", "Cafetería"), 201).get("id").asText();
    productoA =
        post(cafeA.token(), "/products", Map.of("categoryId", categoriaA, "name", "Lágrima", "price", 1400), 201)
            .get("id").asText();
    mesaA = id(post(cafeA.token(), "/tables", Map.of("locationId", cafeA.locationId(), "number", 7), 201));
    cajaA = openCash(cafeA.token(), cafeA.locationId(), "1000");
    pedidoA = id(createOrder(cafeA.token(), cafeA.locationId(), mesaA, item(productoA, 1)));
  }

  @Test
  @DisplayName("El café B no puede leer nada del café A por id")
  void cannotReadById() throws Exception {
    get(cafeB.token(), "/products/" + productoA, 404);
    get(cafeB.token(), "/categories/" + categoriaA, 404);
    get(cafeB.token(), "/orders/" + pedidoA, 404);
    get(cafeB.token(), "/tables/" + mesaA, 404);
    get(cafeB.token(), "/locations/" + cafeA.locationId(), 404);
    get(cafeB.token(), "/cash-sessions/" + cajaA, 404);
    get(cafeB.token(), "/users/" + cafeA.userId(), 404);
  }

  @Test
  @DisplayName("Las listas del café B no traen nada del café A")
  void listsAreSeparated() throws Exception {
    assertThat(ids(get(cafeB.token(), "/products"))).doesNotContain(productoA);
    assertThat(ids(get(cafeB.token(), "/categories"))).doesNotContain(categoriaA);
    assertThat(ids(get(cafeB.token(), "/orders"))).doesNotContain(pedidoA);
    assertThat(ids(get(cafeB.token(), "/orders/active"))).doesNotContain(pedidoA);
    assertThat(ids(get(cafeB.token(), "/tables"))).doesNotContain(mesaA);
    assertThat(ids(get(cafeB.token(), "/locations"))).containsExactly(cafeB.locationId());
    assertThat(ids(get(cafeB.token(), "/cash-sessions"))).doesNotContain(cajaA);
    assertThat(ids(get(cafeB.token(), "/users"))).containsExactly(cafeB.userId());
  }

  @Test
  @DisplayName("El café B no puede filtrar por el local del café A")
  void cannotQueryOtherLocation() throws Exception {
    get(cafeB.token(), "/orders?locationId=" + cafeA.locationId(), 404);
    get(cafeB.token(), "/orders/active?locationId=" + cafeA.locationId(), 404);
    get(cafeB.token(), "/cash-sessions/current?locationId=" + cafeA.locationId(), 404);
    get(cafeB.token(), "/stock/products/levels?locationId=" + cafeA.locationId(), 404);
  }

  @Test
  @DisplayName("El café B no puede cargar pedidos con productos, mesas o local del café A")
  void cannotCreateWithForeignIds() throws Exception {
    String productoB = newProduct(cafeB, "Cortado", "1300");

    // Producto de A en el local de B.
    createOrderWithKey(cafeB.token(), cafeB.locationId(), null, UUID.randomUUID().toString(), 404, item(productoA, 1));
    // Producto de B en el local de A.
    createOrderWithKey(cafeB.token(), cafeA.locationId(), null, UUID.randomUUID().toString(), 404, item(productoB, 1));
    // Mesa de A.
    createOrderWithKey(cafeB.token(), cafeB.locationId(), mesaA, UUID.randomUUID().toString(), 404, item(productoB, 1));
  }

  @Test
  @DisplayName("El café B no puede cobrar, cancelar ni agregar a un pedido del café A")
  void cannotTouchForeignOrder() throws Exception {
    String productoB = newProduct(cafeB, "Cortado", "1300");
    openCash(cafeB.token(), cafeB.locationId(), "0");

    pay(cafeB.token(), pedidoA, "CASH", "1400", 404);
    setStatus(cafeB.token(), pedidoA, "CANCELLED", 404);
    addItems(cafeB.token(), pedidoA, 404, item(productoB, 1));

    // Y el pedido de A sigue intacto.
    JsonNode pedido = get(cafeA.token(), "/orders/" + pedidoA);
    assertThat(pedido.get("status").asText()).isEqualTo("PENDING");
    assertThat(pedido.get("payments")).isEmpty();
    assertThat(pedido.get("items")).hasSize(1);
  }

  @Test
  @DisplayName("El café B no puede modificar la carta, el local, la caja, el stock ni el equipo del café A")
  void cannotModifyForeignData() throws Exception {
    patch(cafeB.token(), "/products/" + productoA, Map.of("price", 1), 404);
    delete(cafeB.token(), "/products/" + productoA, 404);
    patch(cafeB.token(), "/categories/" + categoriaA, Map.of("name", "Hackeada"), 404);
    patch(cafeB.token(), "/locations/" + cafeA.locationId(), Map.of("name", "Hackeado"), 404);
    post(
        cafeB.token(),
        "/cash-sessions/" + cajaA + "/movements",
        Map.of("type", "EXPENSE", "amount", new BigDecimal("1000"), "reason", "Retiro"),
        404);
    post(cafeB.token(), "/cash-sessions/" + cajaA + "/close", Map.of("countedCashAmount", 0), 404);
    post(
        cafeB.token(),
        "/stock/products/" + productoA + "/adjust",
        Map.of("locationId", cafeB.locationId(), "newQuantity", 0),
        404);
    patch(cafeB.token(), "/users/" + cafeA.userId(), Map.of("password", "otra-clave-123"), 404);
    delete(cafeB.token(), "/users/" + cafeA.userId(), 404);

    // Todo lo de A sigue igual.
    assertMoney(get(cafeA.token(), "/products/" + productoA).get("price"), "1400");
    assertThat(get(cafeA.token(), "/cash-sessions/" + cajaA).get("status").asText()).isEqualTo("OPEN");
    login(cafeA.email(), PASSWORD);
  }
}
