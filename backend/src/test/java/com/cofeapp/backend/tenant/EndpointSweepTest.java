package com.cofeapp.backend.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.cofeapp.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Barrido de seguridad de TODAS las direcciones del servidor (prueba
 * exhaustiva antes de salir a internet, oct. 2026). La lista sale del
 * propio servidor, así que una dirección nueva queda cubierta sola:
 *
 * <ol>
 *   <li>sin iniciar sesión: 401 (salvo las públicas);
 *   <li>con un rol que no corresponde: 403;
 *   <li>el superadmin no entra a nada de un café, ni un café al panel;
 *   <li>con los ids de OTRO café: nunca 2xx, nunca le devuelve datos de
 *       ese café, y después de todo el barrido los datos del otro café
 *       siguen exactamente iguales;
 *   <li>nunca un error interno (5xx).
 * </ol>
 */
@DisplayName("Barrido de seguridad de todas las direcciones")
class EndpointSweepTest extends IntegrationTest {

  private static final Set<String> CAFE_ROLES = Set.of("OWNER", "ADMIN", "SUPERVISOR", "WAITER", "KITCHEN");
  private static final Pattern ROLE = Pattern.compile("'([A-Z_]+)'");
  private static final Pattern VAR = Pattern.compile("\\{([^}]+)}");

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping mappings;

  private record Endpoint(HttpMethod method, String path, Set<String> roles, boolean isPublic) {
    String label() {
      return method + " " + path;
    }
  }

  private record Res(int status, String body) {}

  @Test
  @DisplayName("Sin sesión, con el rol equivocado o desde el panel de superadmin: no entra; nunca un error interno")
  void authenticationAndRoles() throws Exception {
    Tenant cafe = newBusiness();
    Map<String, String> tokens = new HashMap<>();
    tokens.put("OWNER", cafe.token());
    for (String role : List.of("ADMIN", "SUPERVISOR", "WAITER", "KITCHEN")) tokens.put(role, newStaff(cafe, role).token());
    String admin = login("superadmin@test.local", "clave-superadmin-1");

    List<String> problems = new ArrayList<>();
    List<Endpoint> endpoints = endpoints();
    System.out.println("BARRIDO: " + endpoints.size() + " direcciones");
    assertThat(endpoints).hasSizeGreaterThan(100);
    for (Endpoint e : endpoints) {
      String url = fill(e.path(), Map.of());
      // 1. Sin sesión.
      Res anon = send(e.method(), url, null, "{}");
      if (!e.isPublic() && anon.status() != 401) problems.add(e.label() + " sin sesión → " + anon.status());
      if (anon.status() >= 500) problems.add(e.label() + " sin sesión → error interno " + anon.status());
      if (e.isPublic()) continue;

      boolean platform = e.path().startsWith("/platform");
      // 2. Roles del café.
      for (String role : CAFE_ROLES) {
        Res r = send(e.method(), url, tokens.get(role), "{}");
        boolean allowed = !platform && (e.roles() == null || e.roles().contains(role));
        // Un rol sin permiso nunca puede lograrlo. Puede recibir 400 en vez de 403 si el cuerpo
        // vacío no pasa la validación (Spring valida antes de revisar el rol): igual no hace nada.
        if (!allowed && (r.status() < 300 || (r.status() != 403 && r.status() != 400)))
          problems.add(e.label() + " como " + role + " → " + r.status() + " (esperado 403)");
        if (allowed && r.status() == 403) problems.add(e.label() + " como " + role + " → 403 (debería poder)");
        if (r.status() >= 500) problems.add(e.label() + " como " + role + " → error interno " + r.status() + ": " + r.body());
      }
      // 3. Superadmin: solo el panel.
      Res sa = send(e.method(), url, admin, "{}");
      if (!platform && sa.status() != 403) problems.add(e.label() + " como superadmin → " + sa.status() + " (esperado 403)");
      if (sa.status() >= 500) problems.add(e.label() + " como superadmin → error interno " + sa.status() + ": " + sa.body());
    }
    assertThat(problems).as("problemas encontrados").isEmpty();
  }

  @Test
  @DisplayName("Con los ids y el local de otro café: nunca entra, nunca ve sus datos y no le cambia nada")
  void crossTenant() throws Exception {
    Tenant mine = newBusiness();
    Tenant other = newBusiness();
    Map<String, String> ids = buildOtherCafe(other);
    Map<String, String> before = snapshot(other, ids);

    // Un cuerpo con campos de todo tipo, por si alguna dirección los acepta.
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", "HACK");
    body.put("label", "HACK");
    body.put("status", "CANCELLED");
    body.put("firstName", "HACK");
    body.put("isActive", false);
    body.put("mode", "OFF");
    body.put("price", 1);
    body.put("number", 999);
    body.put("newQuantity", 0);
    body.put("countedCashAmount", 0);
    body.put("amount", 1);
    body.put("paymentMethod", "CASH");
    body.put("type", "PERCENTAGE");
    body.put("value", 99);
    body.put("reason", "HACK");
    body.put("stockMode", "NONE");
    body.put("locationId", ids.get("location"));
    body.put("items", List.of(Map.of("productId", ids.get("product"), "quantity", 1)));
    body.put("tables", List.of());
    body.put("options", List.of());
    body.put("productIds", List.of(ids.get("product")));
    body.put("minSelect", 0);
    body.put("concept", "HACK");
    String json = this.json.writeValueAsString(body);

    List<String> problems = new ArrayList<>();
    for (Endpoint e : endpoints()) {
      if (e.isPublic() || e.path().startsWith("/platform")) continue;
      boolean hasVar = e.path().contains("{");
      String url = fill(e.path(), ids);
      // Las listas: con el local del otro café.
      if (!hasVar) url = url + (url.contains("?") ? "&" : "?") + "locationId=" + ids.get("location");
      Res r = send(e.method(), url, mine.token(), json);
      if (r.status() >= 500) problems.add(e.label() + " → error interno " + r.status() + ": " + r.body());
      if (hasVar && r.status() >= 200 && r.status() < 300) problems.add(e.label() + " con ids del otro café → " + r.status());
      // Un error puede repetir el id que se pidió ("No se encontró el pedido X"): eso no es una fuga.
      for (Map.Entry<String, String> id : ids.entrySet()) {
        if (r.status() < 300 && r.body() != null && r.body().contains(id.getValue())) {
          problems.add(e.label() + " devolvió datos del otro café (" + id.getKey() + ")");
        }
      }
    }
    assertThat(problems).as("problemas encontrados").isEmpty();
    assertThat(snapshot(other, ids)).as("los datos del otro café no cambiaron").isEqualTo(before);
  }

  @Test
  @DisplayName("Datos basura en cada dirección (textos enormes, números negativos, JSON roto): nunca un error interno")
  void garbageInput() throws Exception {
    Tenant cafe = newBusiness();
    Map<String, String> ids = buildOtherCafe(cafe); // esta vez, con sus propios datos
    String huge = "x".repeat(5000);
    List<String> bodies = new ArrayList<>();
    Map<String, Object> big = new LinkedHashMap<>();
    for (String f : List.of("name", "label", "notes", "reason", "email", "password", "firstName", "lastName", "orderRef", "unit", "description",
        "status", "mode", "kind", "type", "paymentMethod", "stockMode", "currentPassword", "newPassword", "message", "concept")) big.put(f, huge);
    bodies.add(json.writeValueAsString(big));
    Map<String, Object> negative = new LinkedHashMap<>();
    for (String f : List.of("price", "amount", "number", "capacity", "quantity", "newQuantity", "value", "openingAmount", "countedCashAmount",
        "commissionPct", "minStock", "x", "y", "width", "height", "position", "trialDays", "minSelect", "maxSelect")) negative.put(f, -99999999999.5);
    negative.put("locationId", ids.get("location"));
    negative.put("items", List.of(Map.of("productId", ids.get("product"), "quantity", -5)));
    bodies.add(json.writeValueAsString(negative));
    bodies.add("{\"name\": ");      // JSON roto
    bodies.add("[1, 2, 3]");           // un arreglo en vez de un objeto
    bodies.add("{\"items\": \"no-es-una-lista\", \"price\": \"mil\", \"locationId\": 12}");

    List<String> problems = new ArrayList<>();
    for (Endpoint e : endpoints()) {
      if (e.isPublic() && !e.path().startsWith("/auth")) continue;
      if (e.method() == HttpMethod.GET || e.method() == HttpMethod.DELETE || e.path().startsWith("/platform")) continue;
      String url = fill(e.path(), ids);
      for (String body : bodies) {
        Res r = send(e.method(), url, e.isPublic() ? null : cafe.token(), body);
        if (r.status() >= 500) problems.add(e.label() + " con " + body.substring(0, Math.min(40, body.length())) + "… → " + r.status() + ": " + r.body());
      }
    }
    assertThat(problems).as("errores internos").isEmpty();
  }

  // ---------------------------------------------------------------------

  /** Arma un café con un poco de todo y devuelve sus ids por tipo. */
  private Map<String, String> buildOtherCafe(Tenant cafe) throws Exception {
    Map<String, String> ids = new LinkedHashMap<>();
    ids.put("location", cafe.locationId());
    String product = newProduct(cafe, "Producto ajeno", "1000");
    ids.put("product", product);
    ids.put("category", get(cafe.token(), "/products/" + product).get("categoryId").asText());
    ids.put("table", id(post(cafe.token(), "/tables", Map.of("locationId", cafe.locationId(), "number", 1), 201)));
    ids.put("user", newStaff(cafe, "WAITER").userId());
    ids.put("cash", openCash(cafe.token(), cafe.locationId(), "100"));
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), ids.get("table"), item(product, 3));
    ids.put("order", id(order));
    JsonNode discounted = post(cafe.token(), "/orders/" + id(order) + "/discounts", Map.of("type", "PERCENTAGE", "value", 10, "reason", "x"), 200);
    ids.put("orderDiscount", discounted.get("discounts").get(0).get("id").asText());
    ids.put("orderItem", order.get("items").get(0).get("id").asText());
    JsonNode paid = pay(cafe.token(), id(order), "CASH", "100", 201);
    ids.put("payment", paid.get("payments").get(0).get("id").asText());
    ids.put("ingredient", id(post(cafe.token(), "/stock/ingredients", Map.of("name", "Leche ajena", "unit", "ml"), 201)));
    ids.put("discount", id(post(cafe.token(), "/discounts", Map.of("name", "Ajeno", "type", "PERCENTAGE", "value", 5), 201)));
    Map<String, Object> group = new HashMap<>();
    group.put("name", "Tamaño ajeno");
    group.put("minSelect", 0);
    group.put("maxSelect", 1);
    group.put("options", List.of(Map.of("name", "Grande", "priceDelta", new BigDecimal("100"))));
    group.put("productIds", List.of(product));
    ids.put("optionGroup", id(post(cafe.token(), "/option-groups", group, 201)));
    ids.put("area", id(post(cafe.token(), "/floor/areas", Map.of("locationId", cafe.locationId(), "name", "Salón ajeno"), 201)));
    ids.put("channel", id(post(cafe.token(), "/sales-channels", Map.of("locationId", cafe.locationId(), "kind", "PEDIDOSYA", "mode", "MANUAL"), 201)));
    return ids;
  }

  /** Lo que el otro café ve de sus propias cosas (sin las fechas de "visto"). */
  private Map<String, String> snapshot(Tenant cafe, Map<String, String> ids) throws Exception {
    Map<String, String> out = new LinkedHashMap<>();
    out.put("product", get(cafe.token(), "/products/" + ids.get("product")).toString());
    out.put("category", get(cafe.token(), "/categories/" + ids.get("category")).toString());
    out.put("table", get(cafe.token(), "/tables/" + ids.get("table")).toString());
    out.put("order", get(cafe.token(), "/orders/" + ids.get("order")).toString());
    out.put("cash", get(cafe.token(), "/cash-sessions/" + ids.get("cash")).toString());
    out.put("users", get(cafe.token(), "/users").toString());
    out.put("ingredients", get(cafe.token(), "/stock/ingredients").toString());
    out.put("discounts", get(cafe.token(), "/discounts").toString());
    out.put("optionGroups", get(cafe.token(), "/option-groups").toString());
    out.put("floor", get(cafe.token(), "/floor?locationId=" + ids.get("location")).toString());
    out.put("channels", get(cafe.token(), "/sales-channels?locationId=" + ids.get("location")).toString());
    out.put("location", get(cafe.token(), "/locations/" + ids.get("location")).toString());
    out.put("business", get(cafe.token(), "/businesses/me").toString());
    return out;
  }

  /** Reemplaza cada {variable} por el id del tipo que corresponde (o uno inventado). */
  private static String fill(String path, Map<String, String> ids) {
    Matcher m = VAR.matcher(path);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      String before = path.substring(0, m.start());
      String segment = before.substring(before.lastIndexOf('/', before.length() - 2) + 1).replace("/", "");
      String var = m.group(1);
      String key =
          switch (var) {
            case "orderId" -> "order";
            case "itemId", "orderItemId" -> "orderItem";
            case "discountId", "orderDiscountId" -> "orderDiscount";
            case "productId" -> "product";
            case "ingredientId" -> "ingredient";
            case "locationId" -> "location";
            default ->
                switch (segment) {
                  case "products" -> "product";
                  case "categories" -> "category";
                  case "tables" -> "table";
                  case "orders" -> "order";
                  case "users" -> "user";
                  case "locations" -> "location";
                  case "cash-sessions" -> "cash";
                  case "ingredients" -> "ingredient";
                  case "discounts" -> before.contains("/orders/") ? "orderDiscount" : "discount";
                  case "option-groups" -> "optionGroup";
                  case "areas" -> "area";
                  case "sales-channels" -> "channel";
                  case "payments" -> "payment";
                  default -> "";
                };
          };
      m.appendReplacement(sb, Matcher.quoteReplacement(ids.getOrDefault(key, UUID.randomUUID().toString())));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private List<Endpoint> endpoints() {
    List<Endpoint> out = new ArrayList<>();
    for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.getHandlerMethods().entrySet()) {
      HandlerMethod handler = entry.getValue();
      if (!handler.getBeanType().getPackageName().startsWith("com.cofeapp")) continue;
      Set<String> roles = rolesOf(handler.getMethod(), handler.getBeanType());
      for (String pattern : entry.getKey().getPatternValues()) {
        for (RequestMethod rm : entry.getKey().getMethodsCondition().getMethods()) {
          out.add(new Endpoint(HttpMethod.valueOf(rm.name()), pattern, roles, isPublic(pattern)));
        }
      }
    }
    out.sort(java.util.Comparator.comparing(Endpoint::label));
    return out;
  }

  private static boolean isPublic(String path) {
    return Set.of(
                "/auth/register-business", "/auth/login", "/auth/forgot-password", "/auth/reset-password",
                "/webhooks/mercadopago", "/webhooks/mercadopago-suscripciones", "/legal/requests", "/legal/version",
                "/support/info")
            .contains(path)
        || path.startsWith("/uploads");
  }

  private static Set<String> rolesOf(Method method, Class<?> type) {
    PreAuthorize pre = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
    if (pre == null) pre = AnnotatedElementUtils.findMergedAnnotation(type, PreAuthorize.class);
    if (pre == null) return null;
    Set<String> roles = new java.util.HashSet<>();
    Matcher m = ROLE.matcher(pre.value());
    while (m.find()) roles.add(m.group(1));
    return roles;
  }

  private Res send(HttpMethod method, String url, String token, String body) throws Exception {
    MockHttpServletRequestBuilder req = MockMvcRequestBuilders.request(method, url).accept(MediaType.APPLICATION_JSON);
    if (token != null) req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    if (method != HttpMethod.GET && method != HttpMethod.DELETE) req.contentType(MediaType.APPLICATION_JSON).content(body);
    MvcResult result = mvc.perform(req).andReturn();
    return new Res(result.getResponse().getStatus(), result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  }
}
