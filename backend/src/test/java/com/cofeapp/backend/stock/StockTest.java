package com.cofeapp.backend.stock;

import static org.assertj.core.api.Assertions.assertThat;

import com.cofeapp.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Stock: se descuenta al vender y se repone al cancelar. */
@DisplayName("Stock")
class StockTest extends IntegrationTest {

  @Test
  @DisplayName("Producto con stock directo: baja al vender y al agregar, vuelve al cancelar")
  void directStock() throws Exception {
    Tenant cafe = newBusiness();
    String alfajor = newProduct(cafe, "Alfajor", "900");
    configure(cafe, alfajor, Map.of("stockMode", "DIRECT", "minStock", 2));
    adjustProduct(cafe, alfajor, "10");

    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(alfajor, 3));
    assertThat(productLevel(cafe, alfajor)).isEqualByComparingTo("7");

    addItems(cafe.token(), id(order), 200, item(alfajor, 2));
    assertThat(productLevel(cafe, alfajor)).isEqualByComparingTo("5");

    setStatus(cafe.token(), id(order), "CANCELLED", 200);
    assertThat(productLevel(cafe, alfajor)).isEqualByComparingTo("10");
  }

  @Test
  @DisplayName("Producto con receta: descuenta cada insumo según la cantidad vendida, y lo repone al cancelar")
  void recipeStock() throws Exception {
    Tenant cafe = newBusiness();
    String leche = post(cafe.token(), "/stock/ingredients", Map.of("name", "Leche", "unit", "ml", "minStock", 200), 201)
        .get("id").asText();
    String cafeGrano = post(cafe.token(), "/stock/ingredients", Map.of("name", "Café en grano", "unit", "g"), 201)
        .get("id").asText();
    adjustIngredient(cafe, leche, "1000");
    adjustIngredient(cafe, cafeGrano, "500");

    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    configure(
        cafe,
        cafeConLeche,
        Map.of(
            "stockMode",
            "RECIPE",
            "recipeItems",
            List.of(
                Map.<String, Object>of("ingredientId", leche, "quantityPerUnit", 150),
                Map.<String, Object>of("ingredientId", cafeGrano, "quantityPerUnit", new BigDecimal("18.5")))));

    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 2));
    assertThat(ingredientLevel(cafe, leche)).isEqualByComparingTo("700"); // 1000 − 2 × 150
    assertThat(ingredientLevel(cafe, cafeGrano)).isEqualByComparingTo("463"); // 500 − 2 × 18,5

    setStatus(cafe.token(), id(order), "CANCELLED", 200);
    assertThat(ingredientLevel(cafe, leche)).isEqualByComparingTo("1000");
    assertThat(ingredientLevel(cafe, cafeGrano)).isEqualByComparingTo("500");
  }

  @Test
  @DisplayName("Un pedido entregado no devuelve stock")
  void deliveredOrderKeepsStockConsumed() throws Exception {
    Tenant cafe = newBusiness();
    Staff cocina = newStaff(cafe, "KITCHEN");
    String alfajor = newProduct(cafe, "Alfajor", "900");
    configure(cafe, alfajor, Map.of("stockMode", "DIRECT"));
    adjustProduct(cafe, alfajor, "10");
    openCash(cafe.token(), cafe.locationId(), "0");

    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(alfajor, 1));
    pay(cafe.token(), id(order), "CASH", "900", 201);
    setStatus(cocina.token(), id(order), "IN_PREPARATION", 200);
    setStatus(cocina.token(), id(order), "READY", 200);
    setStatus(cafe.token(), id(order), "DELIVERED", 200);

    assertThat(productLevel(cafe, alfajor)).isEqualByComparingTo("9");
  }

  @Test
  @DisplayName("Por debajo del mínimo se marca como bajo")
  void belowMinimum() throws Exception {
    Tenant cafe = newBusiness();
    String alfajor = newProduct(cafe, "Alfajor", "900");
    configure(cafe, alfajor, Map.of("stockMode", "DIRECT", "minStock", 2));
    adjustProduct(cafe, alfajor, "3");
    assertThat(productLevelRow(cafe, alfajor).get("belowMinimum").asBoolean()).isFalse();

    createOrder(cafe.token(), cafe.locationId(), null, item(alfajor, 2));

    assertThat(productLevelRow(cafe, alfajor).get("belowMinimum").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("Los ajustes manuales quedan registrados con cuánto había, cuánto quedó y la diferencia")
  void adjustmentHistory() throws Exception {
    Tenant cafe = newBusiness();
    String alfajor = newProduct(cafe, "Alfajor", "900");
    configure(cafe, alfajor, Map.of("stockMode", "DIRECT"));
    adjustProduct(cafe, alfajor, "10");
    adjustProduct(cafe, alfajor, "6");

    JsonNode history =
        get(cafe.token(), "/stock/products/" + alfajor + "/adjustments?locationId=" + cafe.locationId());

    assertThat(history).hasSize(2);
    JsonNode latest = history.get(0);
    assertMoney(latest.get("previousQuantity"), "10");
    assertMoney(latest.get("newQuantity"), "6");
    assertMoney(latest.get("delta"), "-4");
    assertThat(latest.get("createdByName").asText()).isEqualTo("Dueño Prueba");
  }

  @Test
  @DisplayName("Un producto sin control de stock no aparece en los niveles ni se descuenta")
  void noStockMode() throws Exception {
    Tenant cafe = newBusiness();
    String agua = newProduct(cafe, "Agua", "700");

    createOrder(cafe.token(), cafe.locationId(), null, item(agua, 5));

    JsonNode levels = get(cafe.token(), "/stock/products/levels?locationId=" + cafe.locationId());
    assertThat(levels.findValuesAsText("productId")).doesNotContain(agua);
  }

  @Test
  @DisplayName("Un mozo puede ver el stock pero no ajustarlo")
  void waiterCannotAdjust() throws Exception {
    Tenant cafe = newBusiness();
    Staff mozo = newStaff(cafe, "WAITER");
    String alfajor = newProduct(cafe, "Alfajor", "900");
    configure(cafe, alfajor, Map.of("stockMode", "DIRECT"));

    get(mozo.token(), "/stock/products/levels?locationId=" + cafe.locationId());
    post(
        mozo.token(),
        "/stock/products/" + alfajor + "/adjust",
        Map.of("locationId", cafe.locationId(), "newQuantity", 100),
        403);
  }

  // ---------------------------------------------------------------------

  private void configure(Tenant cafe, String productId, Map<String, Object> config) throws Exception {
    patch(cafe.token(), "/stock/products/" + productId + "/config", config, 200);
  }

  private void adjustProduct(Tenant cafe, String productId, String quantity) throws Exception {
    Map<String, Object> body =
        Map.of("locationId", cafe.locationId(), "newQuantity", new BigDecimal(quantity), "reason", "Conteo");
    post(cafe.token(), "/stock/products/" + productId + "/adjust", body, 200);
  }

  private void adjustIngredient(Tenant cafe, String ingredientId, String quantity) throws Exception {
    Map<String, Object> body =
        Map.of("locationId", cafe.locationId(), "newQuantity", new BigDecimal(quantity), "reason", "Conteo");
    post(cafe.token(), "/stock/ingredients/" + ingredientId + "/adjust", body, 200);
  }

  private JsonNode productLevelRow(Tenant cafe, String productId) throws Exception {
    for (JsonNode row : get(cafe.token(), "/stock/products/levels?locationId=" + cafe.locationId())) {
      if (row.get("productId").asText().equals(productId)) return row;
    }
    throw new AssertionError("El producto " + productId + " no aparece en los niveles de stock.");
  }

  private BigDecimal productLevel(Tenant cafe, String productId) throws Exception {
    return new BigDecimal(productLevelRow(cafe, productId).get("currentStock").asText());
  }

  private BigDecimal ingredientLevel(Tenant cafe, String ingredientId) throws Exception {
    for (JsonNode row : get(cafe.token(), "/stock/ingredients/levels?locationId=" + cafe.locationId())) {
      if (row.get("ingredientId").asText().equals(ingredientId)) {
        return new BigDecimal(row.get("currentStock").asText());
      }
    }
    throw new AssertionError("El insumo " + ingredientId + " no aparece en los niveles de stock.");
  }
}
