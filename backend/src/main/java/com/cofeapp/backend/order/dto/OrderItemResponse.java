package com.cofeapp.backend.order.dto;

import com.cofeapp.backend.domain.OrderItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderItemResponse(
    String id,
    String productId,
    String productName,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal discountAmount,
    BigDecimal subtotal,
    String notes,
    int batch,
    List<OrderItemOptionResponse> options,
    /** V14 — cuántas unidades ya se cobraron "por productos" (0 si se cobró todo junto o en partes iguales). */
    int paidQuantity,
    Instant createdAt) {

  /** {@code options}: lo elegido en este ítem (V11 — "Leche: Almendra", "Extra shot"), en el orden de los grupos. */
  public static OrderItemResponse from(OrderItem item, List<OrderItemOptionResponse> options) {
    return from(item, options, 0);
  }

  public static OrderItemResponse from(OrderItem item, List<OrderItemOptionResponse> options, int paidQuantity) {
    return new OrderItemResponse(
        item.getId(),
        item.getProductId(),
        item.getProductName(),
        item.getQuantity(),
        item.getUnitPrice(),
        item.getDiscountAmount(),
        item.getSubtotal(),
        item.getNotes(),
        item.getBatch() != null ? item.getBatch() : 1,
        options != null ? options : List.of(),
        paidQuantity,
        item.getCreatedAt());
  }
}
