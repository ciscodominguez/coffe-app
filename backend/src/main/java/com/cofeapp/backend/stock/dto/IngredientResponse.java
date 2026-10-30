package com.cofeapp.backend.stock.dto;

import com.cofeapp.backend.domain.Ingredient;
import java.math.BigDecimal;
import java.time.Instant;

public record IngredientResponse(
    String id,
    String name,
    String unit,
    BigDecimal minStock,
    boolean isActive,
    Instant createdAt,
    Instant updatedAt) {

  public static IngredientResponse from(Ingredient ingredient) {
    return new IngredientResponse(
        ingredient.getId(),
        ingredient.getName(),
        ingredient.getUnit(),
        ingredient.getMinStock(),
        Boolean.TRUE.equals(ingredient.getIsActive()),
        ingredient.getCreatedAt(),
        ingredient.getUpdatedAt());
  }
}
