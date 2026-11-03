package com.cofeapp.backend.order.dto;

import com.cofeapp.backend.domain.OrderDiscount;
import com.cofeapp.backend.domain.enums.DiscountType;
import java.math.BigDecimal;
import java.time.Instant;

/** Un descuento aplicado al pedido (V12). {@code orderItemId} nulo = sobre todo el pedido. */
public record OrderDiscountResponse(
    String id,
    String orderItemId,
    String discountId,
    String name,
    DiscountType type,
    BigDecimal value,
    BigDecimal amount,
    boolean supervisorOnly,
    String reason,
    String createdById,
    String createdByName,
    Instant createdAt) {

  public static OrderDiscountResponse from(OrderDiscount discount, String createdByName) {
    return new OrderDiscountResponse(
        discount.getId(),
        discount.getOrderItemId(),
        discount.getDiscountId(),
        discount.getName(),
        discount.getType(),
        discount.getValue(),
        discount.getAmount(),
        Boolean.TRUE.equals(discount.getSupervisorOnly()),
        discount.getReason(),
        discount.getCreatedById(),
        createdByName,
        discount.getCreatedAt());
  }
}
