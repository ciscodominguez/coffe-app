package com.cofeapp.backend.order.dto;

import com.cofeapp.backend.domain.OrderPaymentItem;
import java.math.BigDecimal;

/** V14 — qué pagó un cobro hecho "por productos". */
public record OrderPaymentItemResponse(String orderItemId, int quantity, BigDecimal amount) {

  public static OrderPaymentItemResponse from(OrderPaymentItem item) {
    return new OrderPaymentItemResponse(item.getOrderItemId(), item.getQuantity(), item.getAmount());
  }
}
