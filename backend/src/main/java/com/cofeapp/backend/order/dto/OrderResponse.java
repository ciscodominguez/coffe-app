package com.cofeapp.backend.order.dto;

import com.cofeapp.backend.domain.Order;
import com.cofeapp.backend.domain.enums.OrderStatus;
import com.cofeapp.backend.domain.enums.SalesChannelKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * {@code payments}/{@code paidTotal}/{@code balanceDue} reemplazan al
 * antiguo campo único {@code paymentMethod} (ítems 5/11 — ver el Javadoc
 * de {@code Order} y {@code OrderPayment}): un pedido puede tener 0, 1 o
 * varias líneas de pago, cada una con su propio método. {@code
 * balanceDue} llega a 0 recién cuando está cobrado por completo — {@code
 * OrderService#updateStatus} exige eso antes de dejar pasar a DELIVERED.
 */
public record OrderResponse(
    String id,
    String locationId,
    String tableId,
    String createdById,
    OrderStatus status,
    BigDecimal subtotal,
    BigDecimal discountTotal,
    BigDecimal total,
    String notes,
    String idempotencyKey,
    Instant clientTimestamp,
    Instant createdAt,
    Instant updatedAt,
    Instant closedAt,
    List<OrderItemResponse> items,
    List<OrderPaymentResponse> payments,
    /** V12 — descuentos aplicados (por ítem o a todo el pedido). */
    List<OrderDiscountResponse> discounts,
    BigDecimal paidTotal,
    BigDecimal balanceDue,
    /** V19 — pedido de una app de delivery (todo nulo en un pedido común). */
    String channelId,
    SalesChannelKind channelKind,
    String channelName,
    String channelOrderRef,
    String channelCustomer,
    Instant channelPickupAt,
    Boolean channelPaidByApp,
    BigDecimal channelCommissionPct) {

  public static OrderResponse from(
      Order order,
      List<OrderItemResponse> items,
      List<OrderPaymentResponse> payments,
      List<OrderDiscountResponse> discounts) {
    BigDecimal paidTotal = payments.stream().map(OrderPaymentResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new OrderResponse(
        order.getId(),
        order.getLocationId(),
        order.getTableId(),
        order.getCreatedById(),
        order.getStatus(),
        order.getSubtotal(),
        order.getDiscountTotal(),
        order.getTotal(),
        order.getNotes(),
        order.getIdempotencyKey(),
        order.getClientTimestamp(),
        order.getCreatedAt(),
        order.getUpdatedAt(),
        order.getClosedAt(),
        items,
        payments,
        discounts,
        paidTotal,
        order.getTotal().subtract(paidTotal),
        order.getChannelId(),
        order.getChannelKind(),
        order.getChannelName(),
        order.getChannelOrderRef(),
        order.getChannelCustomer(),
        order.getChannelPickupAt(),
        order.getChannelPaidByApp(),
        order.getChannelCommissionPct());
  }
}
