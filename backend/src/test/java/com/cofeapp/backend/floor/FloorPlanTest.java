package com.cofeapp.backend.floor;

import static org.assertj.core.api.Assertions.assertThat;

import com.cofeapp.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** Plano del salón (V18): zonas, mesas ubicadas y referencias (barra, puerta…). */
@DisplayName("Plano del salón")
class FloorPlanTest extends IntegrationTest {

  @Test
  @DisplayName("Zonas: crear, renombrar, no repetir nombre y borrar (las mesas quedan sin ubicar)")
  void areas() throws Exception {
    Tenant cafe = newBusiness();
    JsonNode salon = post(cafe.token(), "/floor/areas", area(cafe, "Salón"), 201);
    JsonNode vereda = post(cafe.token(), "/floor/areas", area(cafe, "Vereda"), 201);
    assertThat(salon.get("position").asInt()).isZero();
    assertThat(vereda.get("position").asInt()).isEqualTo(1);

    JsonNode dup = post(cafe.token(), "/floor/areas", area(cafe, "  salón "), 409);
    assertThat(dup.get("message").asText()).contains("Ya hay una zona");
    patch(cafe.token(), "/floor/areas/" + id(vereda), Map.of("name", "Salón"), 409);
    assertThat(patch(cafe.token(), "/floor/areas/" + id(vereda), Map.of("name", "Patio"), 200).get("name").asText())
        .isEqualTo("Patio");

    String mesa = newTable(cafe, 1);
    call(HttpMethod.PUT, "/floor/areas/" + id(salon) + "/layout", cafe.token(),
        layout(List.of(place(mesa, 10, 10, 12, 12, "ROUND")), List.of(ref(null, "BAR", "Barra", 0, 0, 30, 8))), 200);

    delete(cafe.token(), "/floor/areas/" + id(salon), 204);
    JsonNode plan = plan(cafe);
    assertThat(plan.get("areas")).singleElement().satisfies(a -> assertThat(a.get("name").asText()).isEqualTo("Patio"));
    assertThat(plan.get("items")).isEmpty();
    JsonNode table = plan.get("tables").get(0);
    assertThat(table.get("areaId").isNull()).isTrue();
    assertThat(table.get("x").isNull()).isTrue();
    assertThat(table.get("shape").asText()).isEqualTo("ROUND"); // la forma se recuerda
  }

  @Test
  @DisplayName("Guardar la zona: ubica mesas, saca las que no vienen y actualiza/borra referencias")
  void saveLayout() throws Exception {
    Tenant cafe = newBusiness();
    String salon = id(post(cafe.token(), "/floor/areas", area(cafe, "Salón"), 201));
    String vereda = id(post(cafe.token(), "/floor/areas", area(cafe, "Vereda"), 201));
    String m1 = newTable(cafe, 1);
    String m2 = newTable(cafe, 2);
    String m3 = newTable(cafe, 3);

    JsonNode plan = putLayout(cafe, salon,
        layout(
            List.of(place(m1, 5, 5, 10, 10, "ROUND"), place(m2, 20.555, 5, 18, 10, "RECT")),
            List.of(ref(null, "BAR", "Barra", 0, 65, 40, 10), ref(null, "DOOR", null, 90, 30, 10, 12))),
        200);
    JsonNode t2 = table(plan, m2);
    assertThat(t2.get("areaId").asText()).isEqualTo(salon);
    assertMoney(t2.get("x"), "20.56");
    assertThat(t2.get("shape").asText()).isEqualTo("RECT");
    assertThat(table(plan, m3).get("areaId").isNull()).isTrue();
    assertThat(plan.get("items")).hasSize(2);
    String barId = null;
    for (JsonNode i : plan.get("items")) if (i.get("kind").asText().equals("BAR")) barId = id(i);

    // La mesa 3 va a la vereda; la vereda no toca lo del salón.
    putLayout(cafe, vereda, layout(List.of(place(m3, 0, 0, 10, 10, "SQUARE")), List.of()), 200);

    // En el salón: se va la mesa 2, se mueve la barra y la puerta se borra.
    plan = putLayout(cafe, salon,
        layout(List.of(place(m1, 50, 30, 10, 10, "ROUND")), List.of(ref(barId, "BAR", "Barra nueva", 0, 60, 50, 15))), 200);
    assertThat(table(plan, m1).get("x").decimalValue()).isEqualByComparingTo("50");
    assertThat(table(plan, m2).get("areaId").isNull()).isTrue();
    assertThat(table(plan, m3).get("areaId").asText()).isEqualTo(vereda);
    assertThat(plan.get("items")).singleElement().satisfies(i -> assertThat(i.get("label").asText()).isEqualTo("Barra nueva"));
    assertThat(id(plan.get("items").get(0))).isEqualTo(barId);

    // Mover la mesa 3 al salón la saca de la vereda.
    plan = putLayout(cafe, salon,
        layout(List.of(place(m1, 50, 30, 10, 10, "ROUND"), place(m3, 70, 30, 10, 10, "SQUARE")), List.of()), 200);
    assertThat(table(plan, m3).get("areaId").asText()).isEqualTo(salon);

    // Una mesa dada de baja no aparece en el plano.
    delete(cafe.token(), "/tables/" + m2, 204);
    assertThat(ids(plan(cafe).get("tables"))).doesNotContain(m2);
  }

  @Test
  @DisplayName("No deja guardar mesas afuera del plano, repetidas, de tamaño inválido ni referencias raras")
  void validation() throws Exception {
    Tenant cafe = newBusiness();
    String salon = id(post(cafe.token(), "/floor/areas", area(cafe, "Salón"), 201));
    String m1 = newTable(cafe, 1);

    JsonNode out = putLayout(cafe, salon, layout(List.of(place(m1, 95, 10, 10, 10, "ROUND")), List.of()), 400);
    assertThat(out.get("message").asText()).contains("La mesa 1", "afuera del plano");
    putLayout(cafe, salon, layout(List.of(place(m1, 10, 70, 10, 10, "ROUND")), List.of()), 400);
    putLayout(cafe, salon, layout(List.of(place(m1, -1, 10, 10, 10, "ROUND")), List.of()), 400);
    JsonNode tiny = putLayout(cafe, salon, layout(List.of(place(m1, 10, 10, 1, 10, "ROUND")), List.of()), 400);
    assertThat(tiny.get("message").asText()).contains("tamaño inválido");
    JsonNode twice = putLayout(cafe, salon,
        layout(List.of(place(m1, 10, 10, 10, 10, "ROUND"), place(m1, 30, 10, 10, 10, "ROUND")), List.of()), 400);
    assertThat(twice.get("message").asText()).contains("aparece dos veces");
    putLayout(cafe, salon, layout(List.of(), List.of(ref(null, "PISCINA", null, 0, 0, 10, 10))), 400);
    putLayout(cafe, salon, layout(List.of(), List.of(ref(null, "WALL", null, 0, 70, 10, 10))), 400);
    // Justo en el borde, sí.
    putLayout(cafe, salon, layout(List.of(place(m1, 90, 65, 10, 10, "SQUARE")), List.of()), 200);
  }

  @Test
  @DisplayName("Cada café ve y toca solo su plano; el mozo lo ve pero no lo cambia")
  void isolationAndRoles() throws Exception {
    Tenant cafe = newBusiness();
    Tenant otro = newBusiness();
    String salon = id(post(cafe.token(), "/floor/areas", area(cafe, "Salón"), 201));
    String mesa = newTable(cafe, 1);
    String mesaAjena = newTable(otro, 7);

    // Otro café: no ve el local, no toca la zona, no puede meter su mesa en mi zona ni la mía en la suya.
    get(otro.token(), "/floor?locationId=" + cafe.locationId(), 404);
    patch(otro.token(), "/floor/areas/" + salon, Map.of("name", "Robada"), 404);
    delete(otro.token(), "/floor/areas/" + salon, 404);
    putLayout(otro, salon, layout(List.of(), List.of()), 404);
    putLayout(cafe, salon, layout(List.of(place(mesaAjena, 0, 0, 10, 10, "ROUND")), List.of()), 404);
    String suya = id(post(otro.token(), "/floor/areas", area(otro, "Salón"), 201)); // mismo nombre, otro café: ok
    putLayout(otro, suya, layout(List.of(place(mesa, 0, 0, 10, 10, "ROUND")), List.of()), 404);
    post(otro.token(), "/floor/areas", area(cafe, "Intruso"), 404); // local de otro café
    assertThat(plan(otro).get("tables")).singleElement().satisfies(t -> assertThat(id(t)).isEqualTo(mesaAjena));

    // Roles: el mozo ve el plano; el administrador lo arma; el mozo y el supervisor no.
    Staff mozo = newStaff(cafe, "WAITER");
    Staff supervisor = newStaff(cafe, "SUPERVISOR");
    Staff admin = newStaff(cafe, "ADMIN");
    assertThat(get(mozo.token(), "/floor?locationId=" + cafe.locationId()).get("areas")).hasSize(1);
    post(mozo.token(), "/floor/areas", area(cafe, "Vereda"), 403);
    call(HttpMethod.PUT, "/floor/areas/" + salon + "/layout", supervisor.token(), layout(List.of(), List.of()), 403);
    delete(mozo.token(), "/floor/areas/" + salon, 403);
    call(HttpMethod.PUT, "/floor/areas/" + salon + "/layout", admin.token(),
        layout(List.of(place(mesa, 0, 0, 10, 10, "ROUND")), List.of()), 200);
    post(admin.token(), "/floor/areas", area(cafe, "Vereda"), 201);
  }

  @Test
  @DisplayName("Una dirección que el servidor no conoce da 404 con un mensaje claro (no 'error interno')")
  void unknownRoute() throws Exception {
    Tenant cafe = newBusiness();
    JsonNode res = get(cafe.token(), "/esto-no-existe?x=1", 404);
    assertThat(res.get("message").asText()).contains("reiniciá el backend");
  }

  // ---------------------------------------------------------------------

  private JsonNode plan(Tenant cafe) throws Exception {
    return get(cafe.token(), "/floor?locationId=" + cafe.locationId());
  }

  private JsonNode putLayout(Tenant cafe, String areaId, Object body, int status) throws Exception {
    return call(HttpMethod.PUT, "/floor/areas/" + areaId + "/layout", cafe.token(), body, status);
  }

  private String newTable(Tenant cafe, int number) throws Exception {
    return id(post(cafe.token(), "/tables", Map.of("locationId", cafe.locationId(), "number", number), 201));
  }

  private static JsonNode table(JsonNode plan, String tableId) {
    for (JsonNode t : plan.get("tables")) if (id(t).equals(tableId)) return t;
    throw new AssertionError("No está la mesa " + tableId);
  }

  private static Map<String, Object> area(Tenant cafe, String name) {
    return Map.of("locationId", cafe.locationId(), "name", name);
  }

  private static Map<String, Object> layout(List<?> tables, List<?> items) {
    return Map.of("tables", tables, "items", items);
  }

  private static Map<String, Object> place(String id, double x, double y, double w, double h, String shape) {
    return Map.of("id", id, "x", BigDecimal.valueOf(x), "y", BigDecimal.valueOf(y), "width", BigDecimal.valueOf(w),
        "height", BigDecimal.valueOf(h), "shape", shape);
  }

  private static Map<String, Object> ref(String id, String kind, String label, double x, double y, double w, double h) {
    Map<String, Object> m = new HashMap<>();
    if (id != null) m.put("id", id);
    m.put("kind", kind);
    if (label != null) m.put("label", label);
    m.put("x", BigDecimal.valueOf(x));
    m.put("y", BigDecimal.valueOf(y));
    m.put("width", BigDecimal.valueOf(w));
    m.put("height", BigDecimal.valueOf(h));
    return m;
  }
}
