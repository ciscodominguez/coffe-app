package com.cofeapp.backend.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;

import com.cofeapp.backend.support.FakeMercadoPagoClient;
import com.cofeapp.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cobro con QR de Mercado Pago (V10), contra un Mercado Pago simulado
 * ({@link FakeMercadoPagoClient}): conectar la cuenta, configurar el local,
 * cobrar, y que el pago quede registrado una sola vez.
 */
@DisplayName("Cobro con QR de Mercado Pago")
class MercadoPagoQrTest extends IntegrationTest {

  @Autowired private FakeMercadoPagoClient mercadoPago;
  @Autowired private QrChargeService qrChargeService;
  @Autowired private JdbcTemplate jdbc;

  // ---------------------------------------------------------------------
  // Conexión y configuración
  // ---------------------------------------------------------------------

  @Test
  @DisplayName("Conectar: un token inventado o rechazado no se guarda; uno válido sí, y cifrado")
  void connectAccount() throws Exception {
    Tenant cafe = newBusiness();

    post(cafe.token(), "/mercadopago/account", Map.of("accessToken", "cualquier-cosa"), 400);
    post(cafe.token(), "/mercadopago/account", Map.of("accessToken", "APP_USR-no-registrado"), 400);
    assertThat(get(cafe.token(), "/mercadopago/account").get("connected").asBoolean()).isFalse();

    String token = "APP_USR-" + unique();
    mercadoPago.allowToken(token, "111" + Math.abs(token.hashCode()), "CAFE_PRUEBA");
    JsonNode status = post(cafe.token(), "/mercadopago/account", Map.of("accessToken", token), 200);

    assertThat(status.get("connected").asBoolean()).isTrue();
    assertThat(status.get("nickname").asText()).isEqualTo("CAFE_PRUEBA");
    assertThat(status.get("testMode").asBoolean()).isFalse();
    assertThat(status.toString()).doesNotContain(token);

    String stored =
        jdbc.queryForObject(
            "select access_token_encrypted from mp_accounts where business_id = ?", String.class, cafe.businessId());
    assertThat(stored).startsWith("v1:").doesNotContain(token);
  }

  @Test
  @DisplayName("Solo dueño y administrador manejan la cuenta de Mercado Pago")
  void onlyManagementConnects() throws Exception {
    Tenant cafe = newBusiness();
    Staff mozo = newStaff(cafe, "WAITER");

    get(mozo.token(), "/mercadopago/account", 403);
    post(mozo.token(), "/mercadopago/account", Map.of("accessToken", "APP_USR-x"), 403);
    patch(mozo.token(), "/mercadopago/locations/" + cafe.locationId(), Map.of("qrMode", "STATIC"), 403);
    // Pero sí puede preguntar si en su local se cobra con QR.
    JsonNode availability = get(mozo.token(), "/mercadopago/availability?locationId=" + cafe.locationId());
    assertThat(availability.get("available").asBoolean()).isFalse();
    assertThat(availability.get("reason").asText()).contains("no está conectado");
  }

  @Test
  @DisplayName("Configurar el local: pide la dirección la primera vez, crea sucursal y caja una sola vez")
  void configureLocation() throws Exception {
    Tenant cafe = newBusiness();
    connect(cafe);
    int storesBefore = mercadoPago.createdStores().size();

    JsonNode error = patch(cafe.token(), "/mercadopago/locations/" + cafe.locationId(), Map.of("qrMode", "DYNAMIC"), 400);
    assertThat(error.get("message").asText()).contains("calle").contains("latitud");

    JsonNode status = patch(cafe.token(), "/mercadopago/locations/" + cafe.locationId(), address("DYNAMIC"), 200);
    JsonNode location = status.get("locations").get(0);
    assertThat(location.get("configured").asBoolean()).isTrue();
    assertThat(location.get("qrImageUrl").asText()).startsWith("https://");
    assertThat(mercadoPago.createdStores()).hasSize(storesBefore + 1);

    // Cambiar a QR impreso no crea otra caja.
    status = patch(cafe.token(), "/mercadopago/locations/" + cafe.locationId(), Map.of("qrMode", "STATIC"), 200);
    assertThat(status.get("locations").get(0).get("qrMode").asText()).isEqualTo("STATIC");
    assertThat(mercadoPago.createdStores()).hasSize(storesBefore + 1);

    assertThat(get(cafe.token(), "/mercadopago/availability?locationId=" + cafe.locationId()).get("available").asBoolean())
        .isTrue();
  }

  // ---------------------------------------------------------------------
  // Cobro
  // ---------------------------------------------------------------------

  @Test
  @DisplayName("QR en pantalla: el cliente paga y el cobro queda registrado como QR en la caja")
  void dynamicQrPaid() throws Exception {
    Tenant cafe = readyCafe("DYNAMIC");
    Staff mozo = newStaff(cafe, "WAITER");
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    String caja = openCash(cafe.token(), cafe.locationId(), "0");
    JsonNode order = createOrder(mozo.token(), cafe.locationId(), null, item(cafeConLeche, 2));

    JsonNode charge = post(mozo.token(), "/orders/" + id(order) + "/qr-charges", null, 200);
    assertThat(charge.get("status").asText()).isEqualTo("PENDING");
    assertThat(charge.get("mode").asText()).isEqualTo("DYNAMIC");
    assertThat(charge.get("qrData").asText()).startsWith("000201");
    assertThat(charge.get("createdAt").isNull()).isFalse();
    assertThat(charge.get("expiresAt").isNull()).isFalse();
    assertMoney(charge.get("amount"), "3000");
    FakeMercadoPagoClient.CreatedOrder sent = lastOrder();
    assertThat(sent.request().externalReference()).isEqualTo(id(charge));
    assertThat(sent.request().mode()).isEqualTo("dynamic");

    // Todavía no pagó.
    assertThat(get(mozo.token(), "/qr-charges/" + id(charge)).get("status").asText()).isEqualTo("PENDING");

    mercadoPago.setOrderStatus(sent.mpOrderId(), "processed");
    JsonNode paid = get(mozo.token(), "/qr-charges/" + id(charge));

    assertThat(paid.get("status").asText()).isEqualTo("PAID");
    assertThat(paid.get("qrData").isNull()).isTrue();
    assertMoney(paid.get("order").get("balanceDue"), "0");
    JsonNode payment = paid.get("order").get("payments").get(0);
    assertThat(payment.get("paymentMethod").asText()).isEqualTo("QR");
    assertThat(payment.get("cashSessionId").asText()).isEqualTo(caja);
    assertThat(payment.get("createdById").asText()).isEqualTo(mozo.userId());

    // Y la caja lo cuenta como venta con QR.
    JsonNode closed = post(cafe.token(), "/cash-sessions/" + caja + "/close", Map.of("countedCashAmount", 0), 200);
    assertMoney(closed.get("salesByMethod").get("QR"), "3000");
  }

  @Test
  @DisplayName("Cobro parcial con QR: se cobra el monto elegido y el resto queda pendiente")
  void partialQrCharge() throws Exception {
    Tenant cafe = readyCafe("DYNAMIC");
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    openCash(cafe.token(), cafe.locationId(), "0");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 2));

    post(cafe.token(), "/orders/" + id(order) + "/qr-charges", Map.of("amount", 3000.01), 409);
    JsonNode charge = post(cafe.token(), "/orders/" + id(order) + "/qr-charges", Map.of("amount", 1000), 200);
    mercadoPago.setOrderStatus(lastOrder().mpOrderId(), "processed");

    JsonNode paid = get(cafe.token(), "/qr-charges/" + id(charge));
    assertMoney(paid.get("order").get("paidTotal"), "1000");
    assertMoney(paid.get("order").get("balanceDue"), "2000");
    // Con el QR ya resuelto, el resto se puede cobrar en efectivo.
    pay(cafe.token(), id(order), "CASH", "2000", 201);
  }

  @Test
  @DisplayName("Cuenta dividida por productos con QR: cobra la parte de esa persona y marca sus productos (también por el proceso de fondo)")
  void qrChargeByItems() throws Exception {
    Tenant cafe = readyCafe("DYNAMIC");
    String latte = newProduct(cafe, "Latte", "1500");
    String tostado = newProduct(cafe, "Tostado", "4000");
    openCash(cafe.token(), cafe.locationId(), "0");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(latte, 2), item(tostado, 1));
    String latteLine = null;
    for (JsonNode line : order.get("items")) {
      if (line.get("productName").asText().equals("Latte")) latteLine = line.get("id").asText();
    }

    Map<String, Object> oneLatte = Map.of("items", java.util.List.of(Map.of("orderItemId", latteLine, "quantity", 1)));
    JsonNode charge = post(cafe.token(), "/orders/" + id(order) + "/qr-charges", oneLatte, 200);
    assertMoney(charge.get("amount"), "1500");
    assertThat(lastOrder().request().amount()).isEqualByComparingTo("1500");

    // Lo confirma el proceso de fondo (sin nadie mirando la ventana).
    mercadoPago.setOrderStatus(lastOrder().mpOrderId(), "processed");
    qrChargeService.syncAllPending();

    JsonNode after = get(cafe.token(), "/orders/" + id(order));
    assertMoney(after.get("paidTotal"), "1500");
    for (JsonNode line : after.get("items")) {
      if (line.get("productName").asText().equals("Latte")) assertThat(line.get("paidQuantity").asInt()).isEqualTo(1);
    }
    JsonNode payment = after.get("payments").get(0);
    assertThat(payment.get("paymentMethod").asText()).isEqualTo("QR");
    assertThat(payment.get("items").get(0).get("orderItemId").asText()).isEqualTo(latteLine);

    // No se puede pedir otro QR por más unidades de las que quedan.
    Map<String, Object> twoLattes = Map.of("items", java.util.List.of(Map.of("orderItemId", latteLine, "quantity", 2)));
    post(cafe.token(), "/orders/" + id(order) + "/qr-charges", twoLattes, 409);
  }

  @Test
  @DisplayName("Mientras el QR espera: no se cobra por otro lado, no se cancela el pedido ni se cierra la caja")
  void pendingChargeBlocksOtherActions() throws Exception {
    Tenant cafe = readyCafe("DYNAMIC");
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    String caja = openCash(cafe.token(), cafe.locationId(), "0");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 1));
    JsonNode charge = post(cafe.token(), "/orders/" + id(order) + "/qr-charges", null, 200);

    pay(cafe.token(), id(order), "CASH", "1500", 409);
    setStatus(cafe.token(), id(order), "CANCELLED", 409);
    post(cafe.token(), "/cash-sessions/" + caja + "/close", Map.of("countedCashAmount", 0), 409);

    // Volver a pedir el QR devuelve el mismo, no crea otro.
    int ordersBefore = mercadoPago.createdOrders().size();
    JsonNode again = post(cafe.token(), "/orders/" + id(order) + "/qr-charges", null, 200);
    assertThat(id(again)).isEqualTo(id(charge));
    assertThat(mercadoPago.createdOrders()).hasSize(ordersBefore);

    // Cancelado el QR, todo vuelve a la normalidad.
    JsonNode cancelled = post(cafe.token(), "/qr-charges/" + id(charge) + "/cancel", null, 200);
    assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
    pay(cafe.token(), id(order), "CASH", "1500", 201);
  }

  @Test
  @DisplayName("Si el cliente ya pagó, cancelar no borra el cobro: queda pagado")
  void cancelAfterPaymentKeepsPayment() throws Exception {
    Tenant cafe = readyCafe("DYNAMIC");
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    openCash(cafe.token(), cafe.locationId(), "0");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 1));
    JsonNode charge = post(cafe.token(), "/orders/" + id(order) + "/qr-charges", null, 200);
    mercadoPago.setOrderStatus(lastOrder().mpOrderId(), "processed");

    JsonNode result = post(cafe.token(), "/qr-charges/" + id(charge) + "/cancel", null, 200);

    assertThat(result.get("status").asText()).isEqualTo("PAID");
    assertMoney(result.get("order").get("balanceDue"), "0");
  }

  @Test
  @DisplayName("QR vencido: no se registra ningún cobro")
  void expiredCharge() throws Exception {
    Tenant cafe = readyCafe("DYNAMIC");
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    openCash(cafe.token(), cafe.locationId(), "0");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 1));
    JsonNode charge = post(cafe.token(), "/orders/" + id(order) + "/qr-charges", null, 200);
    mercadoPago.setOrderStatus(lastOrder().mpOrderId(), "expired");

    JsonNode expired = get(cafe.token(), "/qr-charges/" + id(charge));

    assertThat(expired.get("status").asText()).isEqualTo("EXPIRED");
    assertThat(expired.get("order").get("payments")).isEmpty();
  }

  @Test
  @DisplayName("QR impreso: se le carga el monto, y no admite dos cobros a la vez en el mismo local")
  void staticQrOneAtATime() throws Exception {
    Tenant cafe = readyCafe("STATIC");
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    openCash(cafe.token(), cafe.locationId(), "0");
    JsonNode order1 = createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 1));
    JsonNode order2 = createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 2));

    JsonNode charge1 = post(cafe.token(), "/orders/" + id(order1) + "/qr-charges", null, 200);
    assertThat(charge1.get("mode").asText()).isEqualTo("STATIC");
    assertThat(charge1.get("qrData").isNull()).isTrue();
    assertThat(charge1.get("qrImageUrl").asText()).startsWith("https://");
    assertThat(lastOrder().request().mode()).isEqualTo("static");

    JsonNode busy = post(cafe.token(), "/orders/" + id(order2) + "/qr-charges", null, 409);
    assertThat(busy.get("message").asText()).contains("mostrador");

    post(cafe.token(), "/qr-charges/" + id(charge1) + "/cancel", null, 200);
    post(cafe.token(), "/orders/" + id(order2) + "/qr-charges", null, 200);
  }

  @Test
  @DisplayName("Sin Mercado Pago, sin local configurado o sin caja abierta no se puede cobrar con QR")
  void preconditions() throws Exception {
    Tenant cafe = newBusiness();
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    openCash(cafe.token(), cafe.locationId(), "0");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 1));

    assertThat(post(cafe.token(), "/orders/" + id(order) + "/qr-charges", null, 409).get("message").asText())
        .contains("no está conectado");
    connect(cafe);
    assertThat(post(cafe.token(), "/orders/" + id(order) + "/qr-charges", null, 409).get("message").asText())
        .contains("configurar el QR");

    Tenant otro = readyCafe("DYNAMIC");
    String producto = newProduct(otro, "Tostado", "2500");
    JsonNode sinCaja = createOrder(otro.token(), otro.locationId(), null, item(producto, 1));
    assertThat(post(otro.token(), "/orders/" + id(sinCaja) + "/qr-charges", null, 409).get("message").asText())
        .contains("caja");
  }

  // ---------------------------------------------------------------------
  // Confirmación sin la ventana abierta, y una sola vez
  // ---------------------------------------------------------------------

  @Test
  @DisplayName("El proceso de fondo confirma el pago aunque nadie tenga la ventana abierta, y una sola vez")
  void sweeperConfirmsOnce() throws Exception {
    Tenant cafe = readyCafe("DYNAMIC");
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    openCash(cafe.token(), cafe.locationId(), "0");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 1));
    JsonNode charge = post(cafe.token(), "/orders/" + id(order) + "/qr-charges", null, 200);
    mercadoPago.setOrderStatus(lastOrder().mpOrderId(), "processed");

    qrChargeService.syncAllPending();
    qrChargeService.syncAllPending();
    JsonNode viaScreen = get(cafe.token(), "/qr-charges/" + id(charge));

    assertThat(viaScreen.get("status").asText()).isEqualTo("PAID");
    assertThat(viaScreen.get("order").get("payments")).hasSize(1);
  }

  @Test
  @DisplayName("Aunque varios avisos lleguen al mismo tiempo, el pago se registra una sola vez")
  void concurrentConfirmationsRegisterOnce() throws Exception {
    Tenant cafe = readyCafe("DYNAMIC");
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    openCash(cafe.token(), cafe.locationId(), "0");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 1));
    post(cafe.token(), "/orders/" + id(order) + "/qr-charges", null, 200);
    String mpOrderId = lastOrder().mpOrderId();
    mercadoPago.setOrderStatus(mpOrderId, "processed");

    ExecutorService pool = Executors.newFixedThreadPool(6);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> tasks = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      boolean viaWebhook = i % 2 == 0;
      tasks.add(
          pool.submit(
              () -> {
                start.await();
                if (viaWebhook) qrChargeService.handleWebhook(mpOrderId);
                else qrChargeService.syncAllPending();
                return null;
              }));
    }
    start.countDown();
    for (Future<?> task : tasks) task.get(30, TimeUnit.SECONDS);
    pool.shutdown();

    assertThat(get(cafe.token(), "/orders/" + id(order)).get("payments")).hasSize(1);
  }

  @Test
  @DisplayName("Aviso de Mercado Pago (webhook): confirma consultando a MP; un aviso con id desconocido no hace nada")
  void webhookConfirms() throws Exception {
    Tenant cafe = readyCafe("DYNAMIC");
    String cafeConLeche = newProduct(cafe, "Café con leche", "1500");
    openCash(cafe.token(), cafe.locationId(), "0");
    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(cafeConLeche, 1));
    JsonNode charge = post(cafe.token(), "/orders/" + id(order) + "/qr-charges", null, 200);
    String mpOrderId = lastOrder().mpOrderId();

    // Un aviso trucho (MP todavía dice "created") no marca nada como pagado.
    post(null, "/webhooks/mercadopago", Map.of("type", "order", "data", Map.of("id", mpOrderId)), 200);
    post(null, "/webhooks/mercadopago", Map.of("type", "order", "data", Map.of("id", "ORD-desconocida")), 200);
    assertThat(get(cafe.token(), "/orders/" + id(order)).get("payments")).isEmpty();

    mercadoPago.setOrderStatus(mpOrderId, "processed");
    post(null, "/webhooks/mercadopago", Map.of("type", "order", "data", Map.of("id", mpOrderId)), 200);

    JsonNode reread = get(cafe.token(), "/orders/" + id(order));
    assertThat(reread.get("payments")).hasSize(1);
    assertMoney(reread.get("balanceDue"), "0");
    assertThat(get(cafe.token(), "/qr-charges/" + id(charge)).get("status").asText()).isEqualTo("PAID");
  }

  @Test
  @DisplayName("Otro café no ve ni toca los cobros con QR ajenos")
  void tenantIsolation() throws Exception {
    Tenant cafeA = readyCafe("DYNAMIC");
    Tenant cafeB = readyCafe("DYNAMIC");
    String producto = newProduct(cafeA, "Café con leche", "1500");
    openCash(cafeA.token(), cafeA.locationId(), "0");
    JsonNode order = createOrder(cafeA.token(), cafeA.locationId(), null, item(producto, 1));
    JsonNode charge = post(cafeA.token(), "/orders/" + id(order) + "/qr-charges", null, 200);

    get(cafeB.token(), "/qr-charges/" + id(charge), 404);
    post(cafeB.token(), "/qr-charges/" + id(charge) + "/cancel", null, 404);
    post(cafeB.token(), "/orders/" + id(order) + "/qr-charges", null, 404);
    get(cafeB.token(), "/mercadopago/availability?locationId=" + cafeA.locationId(), 404);
    patch(cafeB.token(), "/mercadopago/locations/" + cafeA.locationId(), Map.of("qrMode", "STATIC"), 404);

    // Y el cobro de A se hizo con el token de A.
    assertThat(lastOrder().token()).isEqualTo(tokenOf(cafeA));
  }

  // ---------------------------------------------------------------------

  private final Map<String, String> tokens = new HashMap<>();

  private void connect(Tenant cafe) throws Exception {
    String token = "APP_USR-" + unique();
    mercadoPago.allowToken(token, String.valueOf(Math.abs(token.hashCode())), "CAFE_" + cafe.businessId().substring(0, 4));
    tokens.put(cafe.businessId(), token);
    post(cafe.token(), "/mercadopago/account", Map.of("accessToken", token), 200);
  }

  private String tokenOf(Tenant cafe) {
    return tokens.get(cafe.businessId());
  }

  /** Café con Mercado Pago conectado y el local configurado en ese modo. */
  private Tenant readyCafe(String mode) throws Exception {
    Tenant cafe = newBusiness();
    connect(cafe);
    patch(cafe.token(), "/mercadopago/locations/" + cafe.locationId(), address(mode), 200);
    return cafe;
  }

  private static Map<String, Object> address(String mode) {
    Map<String, Object> body = new HashMap<>();
    body.put("qrMode", mode);
    body.put("streetName", "Güemes");
    body.put("streetNumber", "2800");
    body.put("cityName", "Mar del Plata");
    body.put("stateName", "Buenos Aires");
    body.put("latitude", -38.0144);
    body.put("longitude", -57.5402);
    return body;
  }

  private FakeMercadoPagoClient.CreatedOrder lastOrder() {
    return mercadoPago.createdOrders().get(mercadoPago.createdOrders().size() - 1);
  }
}
