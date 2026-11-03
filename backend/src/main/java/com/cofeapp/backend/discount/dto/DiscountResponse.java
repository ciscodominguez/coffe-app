package com.cofeapp.backend.discount.dto;

import com.cofeapp.backend.domain.Discount;
import com.cofeapp.backend.domain.enums.DiscountType;
import java.math.BigDecimal;
import java.time.Instant;

public record DiscountResponse(
    String id,
    String name,
    DiscountType type,
    BigDecimal value,
    boolean requiresAuthorization,
    boolean isActive,
    Instant createdAt,
    Instant updatedAt) {

  public static DiscountResponse from(Discount discount) {
    return new DiscountResponse(
        discount.getId(),
        discount.getName(),
        discount.getType(),
        discount.getValue(),
        Boolean.TRUE.equals(discount.getRequiresAuthorization()),
        Boolean.TRUE.equals(discount.getIsActive()),
        discount.getCreatedAt(),
        discount.getUpdatedAt());
  }
}
