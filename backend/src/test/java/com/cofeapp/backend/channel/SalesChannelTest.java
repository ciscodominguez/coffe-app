package com.cofeapp.backend.channel;

import static org.assertj.core.api.Assertions.assertThat;

import com.cofeapp.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Apps de delivery, Fase A (V19): canales por local y pedidos de app cargados a mano. */
@DisplayName("Apps de delivery (carga manual)")
class SalesChannelTest extends IntegrationTest {

  @Test
  @DisplayName("Cada app se prende por separado; nombres fijos, 'otra app' con nombre, sin repetir; el automático todavía no")
  void configureChannels() throws Exception {
    Tenant cafe = newBusiness();
    assertThat(get(cafe.token(), "/sales-channels?locationId=" + cafe.locationId())).isEmpty(); // todo arranca apagado

    JsonNode pya = post(cafe.token(), "/sales-channels", channel(cafe, "PEDIDOSYA", null, "MANUAL", "25"), 201);
    assertThat(pya.get("name").asText()).isEqualTo("PedidosYa");
    assertThat(pya.get("mode").asText()).isEqualTo("MANUAL");
    assertMoney(pya.get("commissionPct"), "25");
    JsonNode dup = post(cafe.token(), "/sales-channels", channel(cafe, "PEDIDOSYA", "Otro nombre", "MANUAL", "10"), 409);
    assertThat(dup.get("message").asText()).contains("PedidosYa ya está");

    post(cafe.token(), "/sales-channels", channel(cafe, "OTHER", " ", "MANUAL", "0"), 400);
    JsonNode other = post(cafe.token(), "/sales-channels", channel(cafe, "OTHER", "  App del barrio ", null, null), 201);
    assertThat(other.get("name").asText()).isEqualTo("App del barrio");
    assertThat(other.get("mode").asText()).isEqualTo("MANUAL");
    post(cafe.token(), "/sales-channels", channel(cafe, "OTHER", "pedidosya", "MANUAL", "0"), 409);

    JsonNode auto = post(cafe.token(), "/sales-channels", channel(cafe, "RAPPI", null, "AUTO", "20"), 400);
    assertThat(auto.get("message").asText()).contains("automático todavía no");
    patch(cafe.token(), "/sales-channels/" + id(pya), Map.of("mode", "AUTO"), 400);
    post(cafe.token(), "/sales-channels", channel(cafe, "RAPPI", null, "MANUAL", "101"), 400);

    JsonNode off = patch(cafe.token(), "/sales-channels/" + id(pya), Map.of("mode", "OFF", "commissionPct", 22.5), 200);
    assertThat(off.get("mode").asText()).isEqualTo("OFF");
    assertMoney(off.get("commissionPct"), "22.5");
    JsonNode renamed = patch(cafe.token(), "/sales-channels/" + id(other), Map.of("name", "La App"), 200);
    assertThat(renamed.get("name").asText()).isEqualTo("La App");
    assertThat(get(cafe.token(), "/sales-channels?locationId=" + cafe.locationId())).hasSize(2);

    // Roles y otro café.
    Staff mozo = newStaff(cafe, "WAITER");
    assertThat(get(mozo.token(), "/sales-channels?locationId=" + cafe.locationId())).hasSize(2);
    post(mozo.token(), "/sales-channels", channel(cafe, "RAPPI", null, "MANUAL", "0"), 403);
    patch(mozo.token(), "/sales-channels/" + id(pya), Map.of("mode", "MANUAL"), 403);
    Tenant otro = newBusiness();
    patch(otro.token(), "/sales-channels/" + id(pya), Map.of("mode", "MANUAL"), 404);
    assertThat(get(otro.token(), "/sales-channels")).isEmpty();
    post(otro.token(), "/sales-channels", channel(cafe, "RAPPI", null, "MANUAL", "0"), 404); // local de otro café
  }

  @Test
  @DisplayName("Pedido de app cobrado por la app: queda pagado sin caja, se entrega y no se puede cargar dos veces")
  void paidByApp() throws Exception {
    Tenant cafe = newBusiness();
    Staff mozo = newStaff(cafe, "WAITER");
    Staff cocina = newStaff(cafe, "KITCHEN");
    String tostado = newProduct(cafe, "Tostado", "4500");
    String pya = id(post(cafe.token(), "/sales-channels", channel(cafe, "PEDIDOSYA", null, "MANUAL", "25"), 201));

    Instant pickup = Instant.now().plus(20, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
    JsonNode order = appOrder(mozo.token(), cafe, pya, " #1234 ", "Lucía", pickup, null, 201, item(tostado, 2));
    assertThat(order.get("channelName").asText()).isEqualTo("PedidosYa");
    assertThat(order.get("channelKind").asText()).isEqualTo("PEDIDOSYA");
    assertThat(order.get("channelOrderRef").asText()).isEqualTo("1234");
    assertThat(order.get("channelCustomer").asText()).isEqualTo("Lucía");
    assertThat(Instant.parse(order.get("channelPickupAt").asText())).isEqualTo(pickup);
    assertThat(order.get("channelPaidByApp").asBoolean()).isTrue();
    assertMoney(order.get("channelCommissionPct"), "25");
    assertThat(order.get("tableId").isNull()).isTrue();
    assertMoney(order.get("total"), "9000");
    assertMoney(order.get("balanceDue"), "0");
    assertThat(order.get("payments")).singleElement().satisfies(p -> {
      assertThat(p.get("paymentMethod").asText()).isEqualTo("APP");
      assertThat(p.get("cashSessionId").isNull()).isTrue(); // sin caja abierta: no entra en ninguna caja
    });

    // El mismo número de pedido (con o sin #) no se carga dos veces.
    JsonNode dup = appOrder(mozo.token(), cafe, pya, "1234", null, null, null, 409, item(tostado, 1));
    assertThat(dup.get("message").asText()).contains("#1234", "PedidosYa", "ya está cargado");

    // Se prepara y se entrega como cualquier pedido, sin abrir la caja.
    setStatus(cocina.token(), id(order), "IN_PREPARATION", 200);
    setStatus(cocina.token(), id(order), "READY", 200);
    assertThat(setStatus(mozo.token(), id(order), "DELIVERED", 200).get("status").asText()).isEqualTo("DELIVERED");

    // Y aparece en la lista con sus datos de app.
    JsonNode listed = get(cafe.token(), "/orders/" + id(order));
    assertThat(listed.get("channelOrderRef").asText()).isEqualTo("1234");
  }

  @Test
  @DisplayName("Pedido de app que cobra el café: se cobra con la caja; el cobro 'App' solo existe para pedidos de app")
  void paidByCafe() throws Exception {
    Tenant cafe = newBusiness();
    String tostado = newProduct(cafe, "Tostado", "4500");
    String rappi = id(post(cafe.token(), "/sales-channels", channel(cafe, "RAPPI", null, "MANUAL", "20"), 201));

    JsonNode order = appOrder(cafe.token(), cafe, rappi, "R-77", null, null, false, 201, item(tostado, 1));
    assertThat(order.get("channelPaidByApp").asBoolean()).isFalse();
    assertMoney(order.get("balanceDue"), "4500");
    assertThat(order.get("payments")).isEmpty();
    pay(cafe.token(), id(order), "CASH", "4500", 409); // sin caja abierta, como cualquier cobro

    String caja = openCash(cafe.token(), cafe.locationId(), "1000");
    pay(cafe.token(), id(order), "CASH", "4500", 201);

    // "App" en un pedido común: no.
    JsonNode normal = createOrder(cafe.token(), cafe.locationId(), null, item(tostado, 1));
    JsonNode wrong = pay(cafe.token(), id(normal), "APP", "4500", 400);
    assertThat(wrong.get("message").asText()).contains("solo para pedidos de apps");

    // Con la caja abierta, lo cobrado por la app queda asociado al turno, pero no suma al efectivo esperado.
    JsonNode paidByApp = appOrder(cafe.token(), cafe, rappi, "R-78", null, null, true, 201, item(tostado, 2));
    assertThat(paidByApp.get("payments").get(0).get("cashSessionId").asText()).isEqualTo(caja);
    JsonNode session = get(cafe.token(), "/cash-sessions/" + caja);
    assertMoney(session.get("expectedCashAmount"), "5500"); // 1000 inicial + 4500 en efectivo
    assertMoney(session.get("salesByMethod").get("APP"), "9000");
  }

  @Test
  @DisplayName("No deja cargar pedidos de una app apagada, en una mesa o de una app que no es del café")
  void validation() throws Exception {
    Tenant cafe = newBusiness();
    String tostado = newProduct(cafe, "Tostado", "4500");
    String pya = id(post(cafe.token(), "/sales-channels", channel(cafe, "PEDIDOSYA", null, "OFF", "25"), 201));
    JsonNode off = appOrder(cafe.token(), cafe, pya, "1", null, null, null, 409, item(tostado, 1));
    assertThat(off.get("message").asText()).contains("PedidosYa está apagada");

    patch(cafe.token(), "/sales-channels/" + pya, Map.of("mode", "MANUAL"), 200);
    String mesa = id(post(cafe.token(), "/tables", Map.of("locationId", cafe.locationId(), "number", 1), 201));
    Map<String, Object> body = orderBody(cafe, pya, "2", null, null, null, item(tostado, 1));
    body.put("tableId", mesa);
    JsonNode table = post(cafe.token(), "/orders", body, 400);
    assertThat(table.get("message").asText()).contains("no va a una mesa");
    appOrder(cafe.token(), cafe, pya, "   ", null, null, null, 400, item(tostado, 1));

    Tenant otro = newBusiness();
    String suTostado = newProduct(otro, "Tostado", "4500");
    appOrder(otro.token(), otro, pya, "3", null, null, null, 404, item(suTostado, 1));
  }

  @Test
  @DisplayName("Excel: ventas por canal, columnas de apps y hoja 'Apps de delivery' con la comisión; un café sin apps lo ve igual que antes")
  void report() throws Exception {
    Tenant cafe = newBusiness();
    Staff cocina = newStaff(cafe, "KITCHEN");
    String tostado = newProduct(cafe, "Tostado", "4000");
    String pya = id(post(cafe.token(), "/sales-channels", channel(cafe, "PEDIDOSYA", null, "MANUAL", "25"), 201));
    openCash(cafe.token(), cafe.locationId(), "0");

    JsonNode app = appOrder(cafe.token(), cafe, pya, "555", null, null, null, 201, item(tostado, 2)); // 8000, comisión 2000
    deliver(cafe, cocina, id(app));
    JsonNode local = createOrder(cafe.token(), cafe.locationId(), null, item(tostado, 1));
    pay(cafe.token(), id(local), "CASH", "4000", 201);
    deliver(cafe, cocina, id(local));

    Map<String, String> sheets = download(cafe.token());
    assertThat(sheets.get("xl/workbook.xml")).contains("name=\"Apps de delivery\"");
    assertThat(sheets.get("xl/worksheets/sheet1.xml")).contains("Ventas por canal", "PedidosYa", "<v>2000.00</v>");
    assertThat(sheets.get("xl/worksheets/sheet2.xml")).contains("App de delivery", "Comisión app", "PedidosYa #555", "<v>8000.00</v>");
    assertThat(sheets.get("xl/worksheets/sheet3.xml")).contains("PedidosYa", "<v>8000.00</v>", "<v>2000.00</v>", "<v>6000.00</v>");

    // Café sin apps: sin hoja de apps ni columnas nuevas.
    Tenant sinApps = newBusiness();
    Staff suCocina = newStaff(sinApps, "KITCHEN");
    String suTostado = newProduct(sinApps, "Tostado", "4000");
    openCash(sinApps.token(), sinApps.locationId(), "0");
    JsonNode o = createOrder(sinApps.token(), sinApps.locationId(), null, item(suTostado, 1));
    pay(sinApps.token(), id(o), "CASH", "4000", 201);
    deliver(sinApps, suCocina, id(o));
    Map<String, String> plain = download(sinApps.token());
    assertThat(plain.get("xl/workbook.xml")).doesNotContain("Apps de delivery");
    assertThat(plain.get("xl/worksheets/sheet1.xml")).doesNotContain("Ventas por canal", "App de delivery");
    assertThat(plain.get("xl/worksheets/sheet2.xml")).doesNotContain("App de delivery", "Comisión app");
  }

  // ---------------------------------------------------------------------

  private static Map<String, Object> channel(Tenant cafe, String kind, String name, String mode, String pct) {
    Map<String, Object> body = new HashMap<>();
    body.put("locationId", cafe.locationId());
    body.put("kind", kind);
    if (name != null) body.put("name", name);
    if (mode != null) body.put("mode", mode);
    if (pct != null) body.put("commissionPct", new BigDecimal(pct));
    return body;
  }

  @SafeVarargs
  private Map<String, Object> orderBody(
      Tenant cafe, String channelId, String ref, String customer, Instant pickup, Boolean paidByApp, Map<String, Object>... items) {
    Map<String, Object> channel = new HashMap<>();
    channel.put("channelId", channelId);
    channel.put("orderRef", ref);
    if (customer != null) channel.put("customerName", customer);
    if (pickup != null) channel.put("pickupAt", pickup.toString());
    if (paidByApp != null) channel.put("paidByApp", paidByApp);
    Map<String, Object> body = new HashMap<>();
    body.put("locationId", cafe.locationId());
    body.put("idempotencyKey", UUID.randomUUID().toString());
    body.put("clientTimestamp", Instant.now().toString());
    body.put("items", new ArrayList<>(List.of(items)));
    body.put("channel", channel);
    return body;
  }

  @SafeVarargs
  private JsonNode appOrder(
      String token, Tenant cafe, String channelId, String ref, String customer, Instant pickup, Boolean paidByApp, int status,
      Map<String, Object>... items)
      throws Exception {
    return post(token, "/orders", orderBody(cafe, channelId, ref, customer, pickup, paidByApp, items), status);
  }

  private void deliver(Tenant cafe, Staff cocina, String orderId) throws Exception {
    setStatus(cocina.token(), orderId, "IN_PREPARATION", 200);
    setStatus(cocina.token(), orderId, "READY", 200);
    setStatus(cafe.token(), orderId, "DELIVERED", 200);
  }

  private Map<String, String> download(String token) throws Exception {
    Instant to = Instant.now().plus(1, ChronoUnit.MINUTES);
    Instant from = to.minus(1, ChronoUnit.DAYS);
    MvcResult result =
        mvc.perform(
                MockMvcRequestBuilders.get("/reports/excel?from=" + from + "&to=" + to)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, String> files = new HashMap<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
      }
    }
    return files;
  }
}
