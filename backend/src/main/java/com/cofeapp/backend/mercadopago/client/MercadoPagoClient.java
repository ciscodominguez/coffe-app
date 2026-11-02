package com.cofeapp.backend.mercadopago.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Lo poco de la API de Mercado Pago que usa el cobro con QR. Es una
 * interfaz para que los tests usen una versión simulada (sin internet ni
 * cuenta real) — ver {@code FakeMercadoPagoClient} en src/test.
 *
 * <p>Todas las llamadas llevan el Access Token del negocio que cobra: cada
 * café cobra en su propia cuenta de Mercado Pago.
 */
public interface MercadoPagoClient {

  /** {@code GET /users/me} — valida el token y devuelve de qué cuenta es. */
  MpUser getMe(String accessToken);

  /** {@code POST /users/{userId}/stores} — crea la sucursal. */
  MpStore createStore(String accessToken, String userId, MpStoreRequest request);

  /** {@code POST /pos} — crea la caja (y su QR fijo). */
  MpPos createPos(String accessToken, MpPosRequest request);

  /** {@code POST /v1/orders} con {@code type=qr}. */
  MpOrder createQrOrder(String accessToken, String idempotencyKey, MpQrOrderRequest request);

  /** {@code GET /v1/orders/{id}}. */
  MpOrder getOrder(String accessToken, String orderId);

  /** {@code POST /v1/orders/{id}/cancel}. */
  MpOrder cancelOrder(String accessToken, String orderId, String idempotencyKey);

  // --- V16: suscripción de COFFE_APP (débito automático a la cuenta de Fran) ---

  /** Crea la suscripción "pendiente": el dueño la confirma en {@code initPoint} (página de Mercado Pago). */
  MpPreapproval createPreapproval(String accessToken, MpPreapprovalRequest request);

  MpPreapproval getPreapproval(String accessToken, String preapprovalId);

  MpPreapproval cancelPreapproval(String accessToken, String preapprovalId);

  /** Un débito de una suscripción (aviso "subscription_authorized_payment"). */
  MpAuthorizedPayment getAuthorizedPayment(String accessToken, String authorizedPaymentId);

  /** Todos los débitos de una suscripción (para no depender solo de los avisos). */
  List<MpAuthorizedPayment> searchAuthorizedPayments(String accessToken, String preapprovalId);

  record MpPreapprovalRequest(
      String reason,
      String externalReference,
      String payerEmail,
      BigDecimal amount,
      String currencyId,
      Instant startDate,
      String backUrl) {}

  record MpPreapproval(
      String id, String status, String initPoint, String externalReference, String payerEmail, String nextPaymentDate) {}

  /**
   * {@code status}: scheduled, processed, recycling (reintentando) o
   * cancelled. {@code paymentStatus}: approved / rejected / … del pago
   * que generó el débito (nulo si todavía no se intentó).
   */
  record MpAuthorizedPayment(
      String id,
      String preapprovalId,
      String status,
      BigDecimal amount,
      String paymentId,
      String paymentStatus,
      String paymentStatusDetail,
      Instant debitDate) {}

  record MpUser(String id, String nickname, String siteId) {}

  record MpStoreRequest(
      String name,
      String externalId,
      String streetName,
      String streetNumber,
      String cityName,
      String stateName,
      double latitude,
      double longitude,
      String reference) {}

  record MpStore(String id, String externalId) {}

  record MpPosRequest(String name, String storeId, String externalStoreId, String externalId) {}

  record MpPos(String id, String externalId, String qrImageUrl) {}

  /**
   * @param mode "dynamic" o "static", como lo espera Mercado Pago.
   * @param expiration duración ISO 8601, ej. "PT15M".
   */
  record MpQrOrderRequest(
      String externalReference,
      String externalPosId,
      String mode,
      BigDecimal amount,
      String description,
      String expiration) {}

  /**
   * @param status created, processed, expired, canceled, failed, refunded, action_required…
   * @param qrData el texto del QR dinámico (nulo en modo static).
   */
  record MpOrder(String id, String status, String statusDetail, String qrData, String externalReference) {}
}
