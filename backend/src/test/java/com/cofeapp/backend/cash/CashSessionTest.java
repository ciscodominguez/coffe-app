package com.cofeapp.backend.cash;

import static org.assertj.core.api.Assertions.assertThat;

import com.cofeapp.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Caja: apertura, movimientos, cierre y diferencia. Si esto falla, el café pierde plata sin enterarse. */
@DisplayName("Caja")
class CashSessionTest extends IntegrationTest {

  @Test
  @DisplayName("No se pueden abrir dos cajas a la vez en el mismo local")
  void onlyOneOpenSession() throws Exception {
    Tenant cafe = newBusiness();
    openCash(cafe.token(), cafe.locationId(), "5000");

    post(cafe.token(), "/cash-sessions/open", Map.of("locationId", cafe.locationId(), "openingAmount", 1000), 409);
  }

  @Test
  @DisplayName("Cierre completo: el efectivo esperado suma ventas en efectivo e ingresos, resta egresos, y marca la diferencia")
  void fullShift() throws Exception {
    Tenant cafe = newBusiness();
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    String tostado = newProduct(cafe, "Tostado", "2500");
    String caja = openCash(cafe.token(), cafe.locationId(), "10000");

    movement(cafe.token(), caja, "INCOME", "2000", "Cambio que trajo el encargado", 201);
    movement(cafe.token(), caja, "EXPENSE", "500", "Bolsa de hielo", 201);

    JsonNode order1 = createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 2));
    pay(cafe.token(), id(order1), "CASH", "3000", 201);
    JsonNode order2 = createOrder(cafe.token(), cafe.locationId(), null, item(tostado, 1));
    pay(cafe.token(), id(order2), "DEBIT_CARD", "2500", 201);

    // Contaron 14.000 en el cajón.
    JsonNode closed = close(cafe.token(), caja, Map.of("countedCashAmount", 14000), 200);

    assertThat(closed.get("status").asText()).isEqualTo("CLOSED");
    assertMoney(closed.get("salesByMethod").get("CASH"), "3000");
    assertMoney(closed.get("salesByMethod").get("DEBIT_CARD"), "2500");
    assertMoney(closed.get("totalSales"), "5500");
    assertMoney(closed.get("cashIncomeMovementsTotal"), "2000");
    assertMoney(closed.get("cashExpenseMovementsTotal"), "500");
    // 10.000 de apertura + 3.000 ventas en efectivo + 2.000 ingreso − 500 egreso. La tarjeta no va al cajón.
    assertMoney(closed.get("expectedCashAmount"), "14500");
    assertMoney(closed.get("countedCashAmount"), "14000");
    assertMoney(closed.get("cashDifference"), "-500"); // faltante
    assertThat(closed.get("movements")).hasSize(2);
    assertThat(closed.get("payments")).hasSize(2);
  }

  @Test
  @DisplayName("Con el conteo por billete, lo contado sale de la suma de los billetes")
  void denominationCount() throws Exception {
    Tenant cafe = newBusiness();
    String caja = openCash(cafe.token(), cafe.locationId(), "12500");

    Map<String, Object> body = new HashMap<>();
    body.put("countedCashAmount", 0); // se ignora: manda el conteo
    body.put(
        "denominationBreakdown",
        List.of(bill("10000", 1), bill("1000", 2), bill("500", 1)));
    JsonNode closed = close(cafe.token(), caja, body, 200);

    assertMoney(closed.get("countedCashAmount"), "12500");
    assertMoney(closed.get("cashDifference"), "0");
    assertThat(closed.get("denominationBreakdown")).hasSize(3);
  }

  @Test
  @DisplayName("Un billete que no existe o repetido en el conteo no deja cerrar (y la caja sigue abierta)")
  void invalidDenominations() throws Exception {
    Tenant cafe = newBusiness();
    String caja = openCash(cafe.token(), cafe.locationId(), "0");

    close(cafe.token(), caja, Map.of("countedCashAmount", 0, "denominationBreakdown", List.of(bill("300", 1))), 400);
    close(
        cafe.token(),
        caja,
        Map.of("countedCashAmount", 0, "denominationBreakdown", List.of(bill("1000", 1), bill("1000", 2))),
        400);

    assertThat(get(cafe.token(), "/cash-sessions/" + caja).get("status").asText()).isEqualTo("OPEN");
  }

  @Test
  @DisplayName("Una caja cerrada no acepta movimientos ni cobros, y se puede abrir el turno siguiente")
  void closedSessionIsFrozen() throws Exception {
    Tenant cafe = newBusiness();
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    String caja = openCash(cafe.token(), cafe.locationId(), "0");
    close(cafe.token(), caja, Map.of("countedCashAmount", 0), 200);

    movement(cafe.token(), caja, "INCOME", "100", "Tarde", 409);
    close(cafe.token(), caja, Map.of("countedCashAmount", 0), 409);
    get(cafe.token(), "/cash-sessions/current?locationId=" + cafe.locationId(), 404);

    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 1));
    pay(cafe.token(), id(order), "CASH", "1500", 409);

    String siguiente = openCash(cafe.token(), cafe.locationId(), "0");
    assertThat(siguiente).isNotEqualTo(caja);
    JsonNode paid = pay(cafe.token(), id(order), "CASH", "1500", 201);
    assertThat(paid.get("payments").get(0).get("cashSessionId").asText()).isEqualTo(siguiente);
  }

  @Test
  @DisplayName("Cada cobro cuenta en la caja del turno en que se hizo")
  void paymentsBelongToTheirShift() throws Exception {
    Tenant cafe = newBusiness();
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 2));

    String manana = openCash(cafe.token(), cafe.locationId(), "0");
    pay(cafe.token(), id(order), "CASH", "1000", 201);
    close(cafe.token(), manana, Map.of("countedCashAmount", 1000), 200);

    String tarde = openCash(cafe.token(), cafe.locationId(), "0");
    pay(cafe.token(), id(order), "CASH", "2000", 201);
    JsonNode cierreTarde = close(cafe.token(), tarde, Map.of("countedCashAmount", 2000), 200);

    assertMoney(cierreTarde.get("totalSales"), "2000");
    assertMoney(get(cafe.token(), "/cash-sessions/" + manana).get("totalSales"), "1000");
  }

  @Test
  @DisplayName("Cocina no maneja la caja")
  void kitchenCannotUseCash() throws Exception {
    Tenant cafe = newBusiness();
    Staff cocina = newStaff(cafe, "KITCHEN");

    post(cocina.token(), "/cash-sessions/open", Map.of("locationId", cafe.locationId(), "openingAmount", 0), 403);
  }

  private JsonNode movement(String token, String cashSessionId, String type, String amount, String reason, int status)
      throws Exception {
    Map<String, Object> body = Map.of("type", type, "amount", new BigDecimal(amount), "reason", reason);
    return post(token, "/cash-sessions/" + cashSessionId + "/movements", body, status);
  }

  private JsonNode close(String token, String cashSessionId, Map<String, Object> body, int status) throws Exception {
    return post(token, "/cash-sessions/" + cashSessionId + "/close", body, status);
  }

  private static Map<String, Object> bill(String denomination, int quantity) {
    return Map.of("denomination", new BigDecimal(denomination), "quantity", quantity);
  }
}
