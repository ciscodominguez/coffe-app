package com.cofeapp.backend.order.dto;

import com.cofeapp.backend.domain.OrderPayment;
import com.cofeapp.backend.domain.enums.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * {@code createdByName} — mejora "nombres reales" pedida por Fran para la
 * caja: quién cobró cada línea, no solo su id. Se resuelve en lote desde
 * {@code OrderService}/{@code CashSessionService} (ver {@code
 * UserRepository#findAllByIdInScoped}) — nunca acá, este record no tiene
 * acceso a un repositorio.
 */
public record OrderPaymentResponse(
    String id,
    String orderId,
    String cashSessionId,
    PaymentMethod paymentMethod,
    BigDecimal amount,
    String createdById,
    String createdByName,
    /** V14 — si fue un cobro "por productos": cuáles pagó. Vacío en un cobro común. */
    List<OrderPaymentItemResponse> items,
    Instant createdAt) {

  public static OrderPaymentResponse from(OrderPayment payment, String createdByName) {
    return from(payment, createdByName, List.of());
  }

  public static OrderPaymentResponse from(
      OrderPayment payment, String createdByName, List<OrderPaymentItemResponse> items) {
    return new OrderPaymentResponse(
        payment.getId(),
        payment.getOrderId(),
        payment.getCashSessionId(),
        payment.getPaymentMethod(),
        payment.getAmount(),
        payment.getCreatedById(),
        createdByName,
        items != null ? items : List.of(),
        payment.getCreatedAt());
  }
}
