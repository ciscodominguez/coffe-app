package com.cofeapp.backend.option;

import static org.assertj.core.api.Assertions.assertThat;

import com.cofeapp.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** Variantes y agregados (V11): grupos de opciones reutilizables. */
@DisplayName("Opciones de producto (tamaño, leche, extras)")
class ProductOptionsTest extends IntegrationTest {

  // ---------------------------------------------------------------------
  // Armado de grupos
  // ---------------------------------------------------------------------

  @Test
  @DisplayName("Un grupo se crea una vez y se asigna a varios productos; cualquiera lo ve, solo el dueño lo edita")
  void reusableGroup() throws Exception {
    Tenant cafe = newBusiness();
    Staff mozo = newStaff(cafe, "WAITER");
    String cortado = newProduct(cafe, "Cortado", "1200");
    String latte = newProduct(cafe, "Latte", "1800");

    JsonNode leche =
        post(
            cafe.token(),
            "/option-groups",
            group("Leche", 0, 1, List.of(option("Entera", "0", true), option("Almendra", "300", false)), List.of(cortado, latte)),
            201);

    assertThat(leche.get("options")).hasSize(2);
    assertThat(leche.get("options").get(0).get("name").asText()).isEqualTo("Entera");
    assertThat(leche.get("options").get(0).get("isDefault").asBoolean()).isTrue();
    assertThat(texts(leche.get("productIds"))).containsExactlyInAnyOrder(cortado, latte);

    JsonNode seenByWaiter = get(mozo.token(), "/option-groups");
    assertThat(seenByWaiter).hasSize(1);
    post(mozo.token(), "/option-groups", group("Tamaño", 1, 1, List.of(option("Chico", "0", false)), null), 403);
    delete(mozo.token(), "/option-groups/" + id(leche), 403);
  }

  @Test
  @DisplayName("No deja guardar un grupo imposible de completar")
  void groupValidation() throws Exception {
    Tenant cafe = newBusiness();
    List<Map<String, Object>> two = List.of(option("A", "0", false), option("B", "0", false));

    post(cafe.token(), "/option-groups", group("Máx < mín", 2, 1, two, null), 400);
    post(cafe.token(), "/option-groups", group("Mín > opciones", 3, null, two, null), 400);
    post(
        cafe.token(),
        "/option-groups",
        group("Dos de entrada", 0, 1, List.of(option("A", "0", true), option("B", "0", true)), null),
        400);
    post(cafe.token(), "/option-groups", group("Repetida", 0, 1, List.of(option("A", "0", false), option("a", "0", false)), null), 400);
    post(cafe.token(), "/option-groups", group("Sin opciones", 0, 1, List.of(), null), 400);

    Map<String, Object> halfIngredient = new HashMap<>(option("Almendra", "300", false));
    halfIngredient.put("ingredientId", newIngredient(cafe, "Leche de almendra", "ml"));
    post(cafe.token(), "/option-groups", group("Insumo sin cantidad", 0, 1, List.of(halfIngredient), null), 400);

    post(cafe.token(), "/option-groups", group("Leche", 0, 1, two, null), 201);
    JsonNode dup = post(cafe.token(), "/option-groups", group("leche", 0, 1, two, null), 409);
    assertThat(dup.get("message").asText()).contains("Ya existe");
  }

  @Test
  @DisplayName("Al editar, las opciones que vienen con id se conservan y las que faltan se borran")
  void editKeepsIds() throws Exception {
    Tenant cafe = newBusiness();
    JsonNode created =
        post(
            cafe.token(),
            "/option-groups",
            group("Leche", 0, 1, List.of(option("Entera", "0", false), option("Soja", "200", false)), null),
            201);
    String enteraId = created.get("options").get(0).get("id").asText();

    Map<String, Object> entera = new HashMap<>(option("Entera", "0", true));
    entera.put("id", enteraId);
    JsonNode updated =
        call(
            HttpMethod.PUT,
            "/option-groups/" + id(created),
            cafe.token(),
            group("Leche", 0, 1, List.of(option("Almendra", "300", false), entera), null),
            200);

    assertThat(updated.get("options")).hasSize(2);
    assertThat(updated.get("options").get(0).get("name").asText()).isEqualTo("Almendra");
    assertThat(updated.get("options").get(1).get("id").asText()).isEqualTo(enteraId);
    assertThat(updated.get("options").get(1).get("isDefault").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("Desde el producto se eligen sus grupos")
  void assignFromProduct() throws Exception {
    Tenant cafe = newBusiness();
    String latte = newProduct(cafe, "Latte", "1800");
    String tamanio = id(post(cafe.token(), "/option-groups", group("Tamaño", 1, 1, List.of(option("Chico", "0", false)), null), 201));
    String leche = id(post(cafe.token(), "/option-groups", group("Leche", 0, 1, List.of(option("Entera", "0", false)), null), 201));

    call(HttpMethod.PUT, "/products/" + latte + "/option-groups", cafe.token(), Map.of("groupIds", List.of(tamanio, leche)), 200);
    assertThat(texts(get(cafe.token(), "/option-groups/" + tamanio).get("productIds"))).containsExactly(latte);

    call(HttpMethod.PUT, "/products/" + latte + "/option-groups", cafe.token(), Map.of("groupIds", List.of(leche)), 200);
    assertThat(get(cafe.token(), "/option-groups/" + tamanio).get("productIds")).isEmpty();
    assertThat(texts(get(cafe.token(), "/option-groups/" + leche).get("productIds"))).containsExactly(latte);
  }

  @Test
  @DisplayName("Se puede cambiar el orden en que se preguntan los grupos (Tamaño antes que Leche)")
  void reorderGroups() throws Exception {
    Tenant cafe = newBusiness();
    Staff mozo = newStaff(cafe, "WAITER");
    String latte = newProduct(cafe, "Latte", "1800");
    JsonNode leche = post(cafe.token(), "/option-groups", group("Leche", 1, 1, List.of(option("Entera", "0", true)), List.of(latte)), 201);
    JsonNode tamanio = post(cafe.token(), "/option-groups", group("Tamaño", 1, 1, List.of(option("Chico", "0", true)), List.of(latte)), 201);

    JsonNode before = createOrder(cafe.token(), cafe.locationId(), null, itemWith(latte, 1, optionId(leche, "Entera"), optionId(tamanio, "Chico")));
    assertThat(before.get("items").get(0).get("options").findValuesAsText("name")).containsExactly("Entera", "Chico");

    call(HttpMethod.PUT, "/option-groups/order", mozo.token(), Map.of("groupIds", List.of(id(tamanio), id(leche))), 403);
    call(HttpMethod.PUT, "/option-groups/order", cafe.token(), Map.of("groupIds", List.of(id(tamanio))), 400);
    JsonNode reordered =
        call(HttpMethod.PUT, "/option-groups/order", cafe.token(), Map.of("groupIds", List.of(id(tamanio), id(leche))), 200);
    assertThat(groupNames(reordered)).containsExactly("Tamaño", "Leche");
    assertThat(groupNames(get(cafe.token(), "/option-groups"))).containsExactly("Tamaño", "Leche");

    JsonNode after = createOrder(cafe.token(), cafe.locationId(), null, itemWith(latte, 1, optionId(leche, "Entera"), optionId(tamanio, "Chico")));
    assertThat(after.get("items").get(0).get("options").findValuesAsText("name")).containsExactly("Chico", "Entera");

    // Uno nuevo va al final.
    post(cafe.token(), "/option-groups", group("Extras", 0, null, List.of(option("Canela", "0", false)), null), 201);
    assertThat(groupNames(get(cafe.token(), "/option-groups"))).containsExactly("Tamaño", "Leche", "Extras");
  }

  // ---------------------------------------------------------------------
  // Tomar pedidos con opciones
  // ---------------------------------------------------------------------

  @Test
  @DisplayName("El precio del ítem suma los extras de las opciones y el pedido muestra lo elegido")
  void orderWithOptions() throws Exception {
    Tenant cafe = newBusiness();
    String latte = newProduct(cafe, "Latte", "1800");
    JsonNode tamanio =
        post(
            cafe.token(),
            "/option-groups",
            group("Tamaño", 1, 1, List.of(option("Chico", "0", true), option("Grande", "600", false)), List.of(latte)),
            201);
    JsonNode leche =
        post(
            cafe.token(),
            "/option-groups",
            group("Leche", 0, 1, List.of(option("Entera", "0", true), option("Almendra", "300", false)), List.of(latte)),
            201);
    JsonNode extras =
        post(
            cafe.token(),
            "/option-groups",
            group("Extras", 0, null, List.of(option("Extra shot", "500", false), option("Canela", "0", false)), List.of(latte)),
            201);

    JsonNode order =
        createOrder(
            cafe.token(),
            cafe.locationId(),
            null,
            itemWith(latte, 2, optionId(tamanio, "Grande"), optionId(leche, "Almendra"), optionId(extras, "Extra shot"), optionId(extras, "Canela")));

    JsonNode line = order.get("items").get(0);
    assertMoney(line.get("unitPrice"), "3200"); // 1800 + 600 + 300 + 500 + 0
    assertMoney(line.get("subtotal"), "6400");
    assertMoney(order.get("total"), "6400");
    assertThat(line.get("options").findValuesAsText("name")).containsExactly("Grande", "Almendra", "Extra shot", "Canela");
    assertThat(line.get("options").get(1).get("groupName").asText()).isEqualTo("Leche");

    // Al volver a leer el pedido (como lo ve Cocina) las opciones siguen ahí, en el mismo orden.
    JsonNode reread = get(cafe.token(), "/orders/" + id(order));
    assertThat(reread.get("items").get(0).get("options").findValuesAsText("name"))
        .containsExactly("Grande", "Almendra", "Extra shot", "Canela");

    // Y al agregar a un pedido abierto también.
    JsonNode more = addItems(cafe.token(), id(order), 200, itemWith(latte, 1, optionId(tamanio, "Chico")));
    assertMoney(more.get("total"), "8200"); // 6400 + 1800
    assertThat(more.get("items").get(1).get("options").findValuesAsText("name")).containsExactly("Chico");
  }

  @Test
  @DisplayName("Rechaza elecciones que no respetan el grupo, con un mensaje claro")
  void selectionRules() throws Exception {
    Tenant cafe = newBusiness();
    String latte = newProduct(cafe, "Latte", "1800");
    String medialuna = newProduct(cafe, "Medialuna", "600");
    JsonNode tamanio =
        post(
            cafe.token(),
            "/option-groups",
            group("Tamaño", 1, 1, List.of(option("Chico", "0", false), option("Grande", "600", false)), List.of(latte)),
            201);
    JsonNode extras =
        post(
            cafe.token(),
            "/option-groups",
            group(
                "Extras",
                0,
                2,
                List.of(option("Shot", "500", false), option("Canela", "0", false), option("Crema", "400", false)),
                List.of(latte)),
            201);
    String chico = optionId(tamanio, "Chico");

    JsonNode missing = orderExpecting(cafe, 400, itemWith(latte, 1));
    assertThat(missing.get("message").asText()).contains("Tamaño");

    orderExpecting(cafe, 400, itemWith(latte, 1, chico, optionId(tamanio, "Grande")));
    JsonNode tooMany =
        orderExpecting(
            cafe, 400, itemWith(latte, 1, chico, optionId(extras, "Shot"), optionId(extras, "Canela"), optionId(extras, "Crema")));
    assertThat(tooMany.get("message").asText()).contains("hasta 2");
    orderExpecting(cafe, 400, itemWith(latte, 1, chico, chico));
    orderExpecting(cafe, 400, itemWith(medialuna, 1, chico));

    orderExpecting(cafe, 201, itemWith(latte, 1, chico, optionId(extras, "Shot"), optionId(extras, "Canela")));
  }

  @Test
  @DisplayName("Una opción marcada como 'no hay' no se puede pedir; si no queda ninguna, el grupo deja de ser obligatorio")
  void unavailableOptions() throws Exception {
    Tenant cafe = newBusiness();
    String latte = newProduct(cafe, "Latte", "1800");
    JsonNode leche =
        post(
            cafe.token(),
            "/option-groups",
            group("Leche", 1, 1, List.of(option("Entera", "0", false), option("Almendra", "300", false)), List.of(latte)),
            201);

    List<Map<String, Object>> edited = new ArrayList<>();
    for (JsonNode existing : leche.get("options")) {
      Map<String, Object> input = new HashMap<>(option(existing.get("name").asText(), existing.get("priceDelta").asText(), false));
      input.put("id", existing.get("id").asText());
      input.put("isAvailable", !existing.get("name").asText().equals("Almendra"));
      edited.add(input);
    }
    call(HttpMethod.PUT, "/option-groups/" + id(leche), cafe.token(), group("Leche", 1, 1, edited, List.of(latte)), 200);

    JsonNode rejected = orderExpecting(cafe, 400, itemWith(latte, 1, optionId(leche, "Almendra")));
    assertThat(rejected.get("message").asText()).contains("no está disponible");

    // Se acabó todo: sin ninguna disponible, se puede vender sin elegir.
    edited.forEach(o -> o.put("isAvailable", false));
    call(HttpMethod.PUT, "/option-groups/" + id(leche), cafe.token(), group("Leche", 1, 1, edited, List.of(latte)), 200);
    orderExpecting(cafe, 201, itemWith(latte, 1));
  }

  @Test
  @DisplayName("Lo vendido queda como se vendió aunque después se cambie el precio o se borre el grupo")
  void soldOptionsAreSnapshots() throws Exception {
    Tenant cafe = newBusiness();
    String latte = newProduct(cafe, "Latte", "1800");
    JsonNode leche =
        post(cafe.token(), "/option-groups", group("Leche", 0, 1, List.of(option("Almendra", "300", false)), List.of(latte)), 201);
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, itemWith(latte, 1, optionId(leche, "Almendra")));

    Map<String, Object> pricier = new HashMap<>(option("Almendra", "900", false));
    pricier.put("id", optionId(leche, "Almendra"));
    call(HttpMethod.PUT, "/option-groups/" + id(leche), cafe.token(), group("Leche", 0, 1, List.of(pricier), List.of(latte)), 200);
    assertMoney(get(cafe.token(), "/orders/" + id(order)).get("items").get(0).get("unitPrice"), "2100");

    delete(cafe.token(), "/option-groups/" + id(leche), 204);
    JsonNode after = get(cafe.token(), "/orders/" + id(order));
    JsonNode option = after.get("items").get(0).get("options").get(0);
    assertThat(option.get("name").asText()).isEqualTo("Almendra");
    assertThat(option.get("groupName").asText()).isEqualTo("Leche");
    assertMoney(option.get("priceDelta"), "300");
    assertMoney(after.get("total"), "2100");

    // Y el producto ya no tiene opciones: se vende solo.
    createOrder(cafe.token(), cafe.locationId(), null, item(latte, 1));
  }

  // ---------------------------------------------------------------------
  // Stock
  // ---------------------------------------------------------------------

  @Test
  @DisplayName("Una opción con insumo lo descuenta (además de la receta) y lo repone al cancelar, aunque se haya editado")
  void optionIngredientsStock() throws Exception {
    Tenant cafe = newBusiness();
    String cafeGrano = newIngredient(cafe, "Café en grano", "g");
    String almendra = newIngredient(cafe, "Leche de almendra", "ml");
    adjustIngredient(cafe, cafeGrano, "1000");
    adjustIngredient(cafe, almendra, "1000");

    String latte = newProduct(cafe, "Latte", "1800");
    patch(
        cafe.token(),
        "/stock/products/" + latte + "/config",
        Map.of("stockMode", "RECIPE", "recipeItems", List.of(Map.of("ingredientId", cafeGrano, "quantityPerUnit", 18))),
        200);

    Map<String, Object> almendraOption = new HashMap<>(option("Almendra", "300", false));
    almendraOption.put("ingredientId", almendra);
    almendraOption.put("ingredientQuantity", 150);
    Map<String, Object> shot = new HashMap<>(option("Extra shot", "500", false));
    shot.put("ingredientId", cafeGrano);
    shot.put("ingredientQuantity", 9);
    JsonNode leche = post(cafe.token(), "/option-groups", group("Leche", 0, 1, List.of(almendraOption), List.of(latte)), 201);
    JsonNode extras = post(cafe.token(), "/option-groups", group("Extras", 0, null, List.of(shot), List.of(latte)), 201);
    assertThat(leche.get("options").get(0).get("ingredientName").asText()).isEqualTo("Leche de almendra");

    JsonNode order =
        createOrder(
            cafe.token(), cafe.locationId(), null, itemWith(latte, 2, optionId(leche, "Almendra"), optionId(extras, "Extra shot")));
    addItems(cafe.token(), id(order), 200, itemWith(latte, 1, optionId(leche, "Almendra")));

    assertThat(ingredientLevel(cafe, cafeGrano)).isEqualByComparingTo("928"); // 1000 − 3×18 − 2×9
    assertThat(ingredientLevel(cafe, almendra)).isEqualByComparingTo("550"); // 1000 − 3×150

    // Cambian la cantidad de la opción después de vender: al cancelar se repone lo que se descontó.
    almendraOption.put("id", optionId(leche, "Almendra"));
    almendraOption.put("ingredientQuantity", 999);
    call(HttpMethod.PUT, "/option-groups/" + id(leche), cafe.token(), group("Leche", 0, 1, List.of(almendraOption), List.of(latte)), 200);

    setStatus(cafe.token(), id(order), "CANCELLED", 200);
    assertThat(ingredientLevel(cafe, cafeGrano)).isEqualByComparingTo("1000");
    assertThat(ingredientLevel(cafe, almendra)).isEqualByComparingTo("1000");
  }

  // ---------------------------------------------------------------------
  // Aislamiento entre negocios
  // ---------------------------------------------------------------------

  @Test
  @DisplayName("Otro negocio no ve ni usa los grupos ajenos")
  void tenantIsolation() throws Exception {
    Tenant cafe = newBusiness();
    Tenant otro = newBusiness();
    String latte = newProduct(cafe, "Latte", "1800");
    String suyo = newProduct(otro, "Latte", "1500");
    JsonNode leche =
        post(cafe.token(), "/option-groups", group("Leche", 0, 1, List.of(option("Almendra", "300", false)), List.of(latte)), 201);

    assertThat(get(otro.token(), "/option-groups")).isEmpty();
    get(otro.token(), "/option-groups/" + id(leche), 404);
    call(HttpMethod.PUT, "/option-groups/" + id(leche), otro.token(), group("Robado", 0, 1, List.of(option("X", "0", false)), null), 404);
    delete(otro.token(), "/option-groups/" + id(leche), 404);
    call(HttpMethod.PUT, "/products/" + suyo + "/option-groups", otro.token(), Map.of("groupIds", List.of(id(leche))), 404);
    post(otro.token(), "/option-groups", group("Mío", 0, 1, List.of(option("X", "0", false)), List.of(latte)), 404);

    JsonNode sneaky =
        createOrderWithKey(
            otro.token(), otro.locationId(), null, java.util.UUID.randomUUID().toString(), 400, itemWith(suyo, 1, optionId(leche, "Almendra")));
    assertThat(sneaky.get("message").asText()).contains("no tiene opciones");
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  private static Map<String, Object> option(String name, String priceDelta, boolean isDefault) {
    Map<String, Object> option = new HashMap<>();
    option.put("name", name);
    option.put("priceDelta", new BigDecimal(priceDelta));
    option.put("isDefault", isDefault);
    return option;
  }

  private static Map<String, Object> group(
      String name, int min, Integer max, List<Map<String, Object>> options, List<String> productIds) {
    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("minSelect", min);
    body.put("maxSelect", max);
    body.put("options", options);
    if (productIds != null) body.put("productIds", productIds);
    return body;
  }

  private static Map<String, Object> itemWith(String productId, int quantity, String... optionIds) {
    return Map.of("productId", productId, "quantity", quantity, "optionIds", List.of(optionIds));
  }

  private static String optionId(JsonNode group, String name) {
    for (JsonNode option : group.get("options")) {
      if (option.get("name").asText().equals(name)) return option.get("id").asText();
    }
    throw new AssertionError("No hay opción " + name + " en " + group);
  }

  @SafeVarargs
  private JsonNode orderExpecting(Tenant cafe, int status, Map<String, Object>... items) throws Exception {
    return createOrderWithKey(cafe.token(), cafe.locationId(), null, java.util.UUID.randomUUID().toString(), status, items);
  }

  private String newIngredient(Tenant tenant, String name, String unit) throws Exception {
    return post(tenant.token(), "/stock/ingredients", Map.of("name", name, "unit", unit), 201).get("id").asText();
  }

  private void adjustIngredient(Tenant tenant, String ingredientId, String quantity) throws Exception {
    post(
        tenant.token(),
        "/stock/ingredients/" + ingredientId + "/adjust",
        Map.of("locationId", tenant.locationId(), "newQuantity", new BigDecimal(quantity), "reason", "Conteo"),
        200);
  }

  private BigDecimal ingredientLevel(Tenant tenant, String ingredientId) throws Exception {
    for (JsonNode row : get(tenant.token(), "/stock/ingredients/levels?locationId=" + tenant.locationId())) {
      if (row.get("ingredientId").asText().equals(ingredientId)) return new BigDecimal(row.get("currentStock").asText());
    }
    throw new AssertionError("No aparece el insumo " + ingredientId);
  }

  private static List<String> groupNames(JsonNode groups) {
    List<String> result = new ArrayList<>();
    groups.forEach(group -> result.add(group.get("name").asText()));
    return result;
  }

  private static List<String> texts(JsonNode array) {
    List<String> result = new ArrayList<>();
    array.forEach(node -> result.add(node.asText()));
    return result;
  }
}
