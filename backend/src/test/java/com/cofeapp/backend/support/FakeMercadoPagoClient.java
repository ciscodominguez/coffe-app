package com.cofeapp.backend.support;

import com.cofeapp.backend.mercadopago.client.MercadoPagoApiException;
import com.cofeapp.backend.mercadopago.client.MercadoPagoClient;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mercado Pago de mentira para los tests: sin internet ni cuenta real.
 *
 * <ul>
 *   <li>Solo acepta los tokens que se registran con {@link #allowToken}.
 *   <li>Cada orden QR nace "created"; el test decide qué le pasa con
 *       {@link #setOrderStatus} (ej. "processed" = el cliente pagó).
 *   <li>Como Mercado Pago, no deja cargar dos cobros a la vez en el mismo
 *       QR fijo (modo static).
 * </ul>
 */
public class FakeMercadoPagoClient implements MercadoPagoClient {

  public record CreatedOrder(String mpOrderId, String token, MpQrOrderRequest request) {}

  private final Map<String, MpUser> users = new ConcurrentHashMap<>();
  private final Map<String, String> orderStatus = new ConcurrentHashMap<>();
  private final Map<String, String> orderPos = new ConcurrentHashMap<>();
  private final Map<String, String> orderMode = new ConcurrentHashMap<>();
  private final List<CreatedOrder> createdOrders = new CopyOnWriteArrayList<>();
  private final List<MpStoreRequest> createdStores = new CopyOnWriteArrayList<>();
  private final List<MpPosRequest> createdPos = new CopyOnWriteArrayList<>();
  private final AtomicInteger sequence = new AtomicInteger();
  private final AtomicInteger getOrderCalls = new AtomicInteger();

  public void allowToken(String token, String userId, String nickname) {
    users.put(token, new MpUser(userId, nickname, "MLA"));
  }

  public void setOrderStatus(String mpOrderId, String status) {
    orderStatus.put(mpOrderId, status);
  }

  public List<CreatedOrder> createdOrders() {
    return createdOrders;
  }

  public List<MpStoreRequest> createdStores() {
    return createdStores;
  }

  public List<MpPosRequest> createdPos() {
    return createdPos;
  }

  public int getOrderCalls() {
    return getOrderCalls.get();
  }

  @Override
  public MpUser getMe(String accessToken) {
    return requireUser(accessToken);
  }

  @Override
  public MpStore createStore(String accessToken, String userId, MpStoreRequest request) {
    requireUser(accessToken);
    createdStores.add(request);
    return new MpStore(String.valueOf(1000 + sequence.incrementAndGet()), request.externalId());
  }

  @Override
  public MpPos createPos(String accessToken, MpPosRequest request) {
    requireUser(accessToken);
    createdPos.add(request);
    String id = String.valueOf(2000 + sequence.incrementAndGet());
    return new MpPos(id, request.externalId(), "https://www.mercadopago.com/qr/" + id + ".png");
  }

  @Override
  public MpOrder createQrOrder(String accessToken, String idempotencyKey, MpQrOrderRequest request) {
    requireUser(accessToken);
    if ("static".equals(request.mode())) {
      boolean busy =
          orderPos.entrySet().stream()
              .anyMatch(
                  e ->
                      e.getValue().equals(request.externalPosId())
                          && "static".equals(orderMode.get(e.getKey()))
                          && "created".equals(orderStatus.get(e.getKey())));
      if (busy) throw new MercadoPagoApiException(409, "Mercado Pago no pudo crear el cobro con QR: la caja ya tiene una orden.");
    }
    String id = "ORD" + sequence.incrementAndGet();
    orderStatus.put(id, "created");
    orderPos.put(id, request.externalPosId());
    orderMode.put(id, request.mode());
    createdOrders.add(new CreatedOrder(id, accessToken, request));
    String qrData = "dynamic".equals(request.mode()) ? "00020101021243650016COM.MERCADOLIBRE0201306364" + id : null;
    return new MpOrder(id, "created", "created", qrData, request.externalReference());
  }

  @Override
  public MpOrder getOrder(String accessToken, String orderId) {
    requireUser(accessToken);
    getOrderCalls.incrementAndGet();
    String status = orderStatus.get(orderId);
    if (status == null) throw new MercadoPagoApiException(404, "Mercado Pago no pudo consultar el cobro: no existe.");
    return new MpOrder(orderId, status, status, null, null);
  }

  @Override
  public MpOrder cancelOrder(String accessToken, String orderId, String idempotencyKey) {
    requireUser(accessToken);
    String status = orderStatus.get(orderId);
    if (!"created".equals(status)) {
      throw new MercadoPagoApiException(409, "Mercado Pago no pudo cancelar el cobro: la orden está " + status + ".");
    }
    orderStatus.put(orderId, "canceled");
    return new MpOrder(orderId, "canceled", "canceled", null, null);
  }

  // --- V16: suscripciones (cuenta de Fran: token "TEST-token-de-fran") ---

  public static final String BILLING_TOKEN = "TEST-token-de-fran";

  private final Map<String, MpPreapproval> preapprovals = new ConcurrentHashMap<>();
  private final Map<String, MpPreapprovalRequest> preapprovalRequests = new ConcurrentHashMap<>();
  private final Map<String, MpAuthorizedPayment> authorizedPayments = new ConcurrentHashMap<>();

  public MpPreapprovalRequest preapprovalRequest(String preapprovalId) {
    return preapprovalRequests.get(preapprovalId);
  }

  /** El dueño confirmó (o no) la suscripción en la página de Mercado Pago. */
  public void setPreapprovalStatus(String preapprovalId, String status) {
    MpPreapproval p = preapprovals.get(preapprovalId);
    preapprovals.put(
        preapprovalId, new MpPreapproval(p.id(), status, p.initPoint(), p.externalReference(), p.payerEmail(), p.nextPaymentDate()));
  }

  /** Mercado Pago hizo (o reintentó) un débito. Devuelve su id (el del aviso). */
  public String charge(String preapprovalId, String authorizedPaymentId, String paymentStatus, java.math.BigDecimal amount, java.time.Instant when) {
    String paymentId = "PAY-" + authorizedPaymentId + "-" + paymentStatus;
    authorizedPayments.put(
        authorizedPaymentId,
        new MpAuthorizedPayment(
            authorizedPaymentId,
            preapprovalId,
            "approved".equals(paymentStatus) ? "processed" : "recycling",
            amount,
            paymentId,
            paymentStatus,
            "approved".equals(paymentStatus) ? "accredited" : "cc_rejected_insufficient_amount",
            when));
    return authorizedPaymentId;
  }

  @Override
  public MpPreapproval createPreapproval(String accessToken, MpPreapprovalRequest request) {
    requireBillingToken(accessToken);
    String id = "PRE" + sequence.incrementAndGet();
    MpPreapproval created =
        new MpPreapproval(
            id,
            "pending",
            "https://www.mercadopago.com.ar/subscriptions/checkout?preapproval_id=" + id,
            request.externalReference(),
            request.payerEmail(),
            null);
    preapprovals.put(id, created);
    preapprovalRequests.put(id, request);
    return created;
  }

  @Override
  public MpPreapproval getPreapproval(String accessToken, String preapprovalId) {
    requireBillingToken(accessToken);
    MpPreapproval p = preapprovals.get(preapprovalId);
    if (p == null) throw new MercadoPagoApiException(404, "Mercado Pago no pudo consultar la suscripción: no existe.");
    return p;
  }

  @Override
  public MpPreapproval cancelPreapproval(String accessToken, String preapprovalId) {
    getPreapproval(accessToken, preapprovalId);
    setPreapprovalStatus(preapprovalId, "cancelled");
    return preapprovals.get(preapprovalId);
  }

  @Override
  public MpAuthorizedPayment getAuthorizedPayment(String accessToken, String authorizedPaymentId) {
    requireBillingToken(accessToken);
    MpAuthorizedPayment payment = authorizedPayments.get(authorizedPaymentId);
    if (payment == null) throw new MercadoPagoApiException(404, "Mercado Pago no pudo consultar el débito: no existe.");
    return payment;
  }

  @Override
  public List<MpAuthorizedPayment> searchAuthorizedPayments(String accessToken, String preapprovalId) {
    requireBillingToken(accessToken);
    return authorizedPayments.values().stream().filter(p -> preapprovalId.equals(p.preapprovalId())).toList();
  }

  private void requireBillingToken(String accessToken) {
    if (!BILLING_TOKEN.equals(accessToken)) {
      throw new MercadoPagoApiException(401, "Mercado Pago rechazó el token de la cuenta (401).");
    }
  }

  private MpUser requireUser(String accessToken) {
    MpUser user = users.get(accessToken);
    if (user == null) throw new MercadoPagoApiException(401, "Mercado Pago rechazó el token de la cuenta (401).");
    return user;
  }
}
