package com.cofeapp.backend.mercadopago.client;

import com.cofeapp.backend.mercadopago.MercadoPagoProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Implementación real de {@link MercadoPagoClient}, contra
 * {@code https://api.mercadopago.com} (configurable en
 * {@code app.mercadopago.api-base-url}).
 *
 * <p>Los cuerpos se arman a mano con los nombres de campo de Mercado Pago
 * (snake_case) en vez de depender de cómo Jackson nombra los records, así
 * lo que viaja es exactamente lo que dice su documentación. Los montos van
 * como texto con dos decimales ("1500.00"), que es lo que pide la API de
 * Orders.
 */
@Slf4j
@Component
public class HttpMercadoPagoClient implements MercadoPagoClient {

  /** Rubro "Gastronomía" en el catálogo de cajas de Mercado Pago. */
  private static final int POS_CATEGORY_GASTRONOMY = 621102;

  private final RestClient http;
  private final ObjectMapper objectMapper;

  public HttpMercadoPagoClient(MercadoPagoProperties properties, ObjectMapper objectMapper) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(5_000);
    requestFactory.setReadTimeout(15_000);
    // Buffering: manda el cuerpo con Content-Length en vez de "chunked",
    // que es lo que mejor toleran las APIs detrás de balanceadores.
    this.http =
        RestClient.builder()
            .baseUrl(properties.apiBaseUrl())
            .requestFactory(new BufferingClientHttpRequestFactory(requestFactory))
            .build();
    this.objectMapper = objectMapper;
  }

  @Override
  public MpUser getMe(String accessToken) {
    JsonNode body = send(() -> http.get().uri("/users/me").headers(h -> h.setBearerAuth(accessToken)), "validar el token");
    return new MpUser(text(body, "id"), text(body, "nickname"), text(body, "site_id"));
  }

  @Override
  public MpStore createStore(String accessToken, String userId, MpStoreRequest request) {
    Map<String, Object> location = new LinkedHashMap<>();
    location.put("street_number", request.streetNumber());
    location.put("street_name", request.streetName());
    location.put("city_name", request.cityName());
    location.put("state_name", request.stateName());
    location.put("latitude", request.latitude());
    location.put("longitude", request.longitude());
    location.put("reference", request.reference());

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("name", request.name());
    payload.put("external_id", request.externalId());
    payload.put("location", location);

    JsonNode body =
        send(
            () ->
                http.post()
                    .uri("/users/{userId}/stores", userId)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload),
            "crear la sucursal");
    return new MpStore(text(body, "id"), text(body, "external_id"));
  }

  @Override
  public MpPos createPos(String accessToken, MpPosRequest request) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("name", request.name());
    payload.put("fixed_amount", true);
    payload.put("store_id", toNumberIfPossible(request.storeId()));
    payload.put("external_store_id", request.externalStoreId());
    payload.put("external_id", request.externalId());
    payload.put("category", POS_CATEGORY_GASTRONOMY);

    JsonNode body =
        send(
            () ->
                http.post()
                    .uri("/pos")
                    .headers(h -> h.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload),
            "crear la caja");
    JsonNode qr = body.path("qr");
    return new MpPos(text(body, "id"), text(body, "external_id"), text(qr, "image"));
  }

  @Override
  public MpOrder createQrOrder(String accessToken, String idempotencyKey, MpQrOrderRequest request) {
    String amount = money(request.amount());

    Map<String, Object> qr = new LinkedHashMap<>();
    qr.put("external_pos_id", request.externalPosId());
    qr.put("mode", request.mode());

    Map<String, Object> item = new LinkedHashMap<>();
    item.put("title", request.description());
    item.put("unit_price", amount);
    item.put("quantity", 1);
    item.put("unit_measure", "unit");

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", "qr");
    payload.put("total_amount", amount);
    payload.put("description", request.description());
    payload.put("external_reference", request.externalReference());
    payload.put("expiration_time", request.expiration());
    payload.put("config", Map.of("qr", qr));
    payload.put("transactions", Map.of("payments", List.of(Map.of("amount", amount))));
    payload.put("items", List.of(item));

    JsonNode body =
        send(
            () ->
                http.post()
                    .uri("/v1/orders")
                    .headers(
                        h -> {
                          h.setBearerAuth(accessToken);
                          h.set("X-Idempotency-Key", idempotencyKey);
                        })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload),
            "crear el cobro con QR");
    return toOrder(body);
  }

  @Override
  public MpOrder getOrder(String accessToken, String orderId) {
    JsonNode body =
        send(() -> http.get().uri("/v1/orders/{id}", orderId).headers(h -> h.setBearerAuth(accessToken)), "consultar el cobro");
    return toOrder(body);
  }

  @Override
  public MpOrder cancelOrder(String accessToken, String orderId, String idempotencyKey) {
    JsonNode body =
        send(
            () ->
                http.post()
                    .uri("/v1/orders/{id}/cancel", orderId)
                    .headers(
                        h -> {
                          h.setBearerAuth(accessToken);
                          h.set("X-Idempotency-Key", idempotencyKey);
                        }),
            "cancelar el cobro");
    return toOrder(body);
  }

  // --- V16: suscripción de COFFE_APP ---

  @Override
  public MpPreapproval createPreapproval(String accessToken, MpPreapprovalRequest request) {
    Map<String, Object> recurring = new LinkedHashMap<>();
    recurring.put("frequency", 1);
    recurring.put("frequency_type", "months");
    recurring.put("start_date", DATE.format(request.startDate()));
    recurring.put("transaction_amount", request.amount().setScale(2, RoundingMode.HALF_UP));
    recurring.put("currency_id", request.currencyId());

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("reason", request.reason());
    payload.put("external_reference", request.externalReference());
    payload.put("payer_email", request.payerEmail());
    payload.put("auto_recurring", recurring);
    payload.put("back_url", request.backUrl());
    payload.put("status", "pending");

    JsonNode body =
        send(
            () ->
                http.post()
                    .uri("/preapproval")
                    .headers(h -> h.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload),
            "crear la suscripción");
    return toPreapproval(body);
  }

  @Override
  public MpPreapproval getPreapproval(String accessToken, String preapprovalId) {
    JsonNode body =
        send(
            () -> http.get().uri("/preapproval/{id}", preapprovalId).headers(h -> h.setBearerAuth(accessToken)),
            "consultar la suscripción");
    return toPreapproval(body);
  }

  @Override
  public MpPreapproval cancelPreapproval(String accessToken, String preapprovalId) {
    JsonNode body =
        send(
            () ->
                http.put()
                    .uri("/preapproval/{id}", preapprovalId)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("status", "cancelled")),
            "cancelar la suscripción");
    return toPreapproval(body);
  }

  @Override
  public MpAuthorizedPayment getAuthorizedPayment(String accessToken, String authorizedPaymentId) {
    JsonNode body =
        send(
            () -> http.get().uri("/authorized_payments/{id}", authorizedPaymentId).headers(h -> h.setBearerAuth(accessToken)),
            "consultar el débito de la suscripción");
    return toAuthorizedPayment(body);
  }

  @Override
  public List<MpAuthorizedPayment> searchAuthorizedPayments(String accessToken, String preapprovalId) {
    JsonNode body =
        send(
            () ->
                http.get()
                    .uri(uri -> uri.path("/authorized_payments/search").queryParam("preapproval_id", preapprovalId).build())
                    .headers(h -> h.setBearerAuth(accessToken)),
            "consultar los débitos de la suscripción");
    List<MpAuthorizedPayment> result = new ArrayList<>();
    body.path("results").forEach(node -> result.add(toAuthorizedPayment(node)));
    return result;
  }

  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZoneOffset.UTC);

  private MpPreapproval toPreapproval(JsonNode body) {
    return new MpPreapproval(
        text(body, "id"),
        text(body, "status"),
        text(body, "init_point"),
        text(body, "external_reference"),
        text(body, "payer_email"),
        text(body, "next_payment_date"));
  }

  private MpAuthorizedPayment toAuthorizedPayment(JsonNode body) {
    JsonNode payment = body.path("payment");
    String amount = text(body, "transaction_amount");
    String date = text(body, "debit_date");
    if (date == null) date = text(body, "last_modified");
    if (date == null) date = text(body, "date_created");
    return new MpAuthorizedPayment(
        text(body, "id"),
        text(body, "preapproval_id"),
        text(body, "status"),
        amount == null ? BigDecimal.ZERO : new BigDecimal(amount),
        text(payment, "id"),
        text(payment, "status"),
        text(payment, "status_detail"),
        parseDate(date));
  }

  private static Instant parseDate(String value) {
    if (value == null || value.isBlank()) return Instant.now();
    try {
      return OffsetDateTime.parse(value).toInstant();
    } catch (DateTimeParseException ex) {
      try {
        return Instant.parse(value);
      } catch (DateTimeParseException ignored) {
        return Instant.now();
      }
    }
  }

  // ---------------------------------------------------------------------

  private MpOrder toOrder(JsonNode body) {
    return new MpOrder(
        text(body, "id"),
        text(body, "status"),
        text(body, "status_detail"),
        text(body.path("type_response"), "qr_data"),
        text(body, "external_reference"));
  }

  /**
   * Ejecuta la llamada y traduce cualquier error a {@link
   * MercadoPagoApiException} con un mensaje entendible. {@code what} es
   * qué se estaba intentando ("crear la caja"), para el mensaje.
   */
  private JsonNode send(java.util.function.Supplier<RestClient.RequestHeadersSpec<?>> request, String what) {
    try {
      String raw =
          request
              .get()
              .retrieve()
              .onStatus(
                  HttpStatusCode::isError,
                  (req, res) -> {
                    String errorBody = StreamUtils.copyToString(res.getBody(), StandardCharsets.UTF_8);
                    int status = res.getStatusCode().value();
                    log.warn("Mercado Pago respondió {} al {}: {}", status, what, errorBody);
                    throw new MercadoPagoApiException(status, describeError(status, errorBody, what));
                  })
              .body(String.class);
      return raw == null || raw.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
    } catch (MercadoPagoApiException ex) {
      throw ex;
    } catch (ResourceAccessException ex) {
      throw new MercadoPagoApiException(
          0, "No se pudo conectar con Mercado Pago para " + what + ". Revisá la conexión a internet.", ex);
    } catch (IOException ex) {
      throw new MercadoPagoApiException(0, "Mercado Pago devolvió una respuesta que no se pudo leer al " + what + ".", ex);
    }
  }

  private String describeError(int status, String body, String what) {
    if (status == 401 || status == 403) {
      return "Mercado Pago rechazó el token de la cuenta (" + status + ") al " + what
          + ". Revisá que sea el Access Token correcto y que no se haya dado de baja.";
    }
    String detail = null;
    try {
      JsonNode json = objectMapper.readTree(body);
      JsonNode errors = json.path("errors");
      if (errors.isArray() && !errors.isEmpty()) {
        detail = text(errors.get(0), "message");
        if (detail == null) detail = text(errors.get(0), "code");
      }
      if (detail == null) detail = text(json, "message");
      if (detail == null) detail = text(json, "error");
    } catch (IOException | RuntimeException ignored) {
      // Cuerpo que no es JSON: se usa el mensaje genérico.
    }
    return "Mercado Pago no pudo " + what + (detail != null ? ": " + detail : " (error " + status + ").");
  }

  private static String text(JsonNode node, String field) {
    if (node == null) return null;
    JsonNode value = node.get(field);
    if (value == null || value.isNull() || value.isMissingNode()) return null;
    return value.asText();
  }

  private static String money(BigDecimal amount) {
    return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }

  private static Object toNumberIfPossible(String id) {
    try {
      return Long.parseLong(id);
    } catch (NumberFormatException ex) {
      return id;
    }
  }
}
