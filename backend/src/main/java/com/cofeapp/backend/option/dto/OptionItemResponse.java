package com.cofeapp.backend.option.dto;

import com.cofeapp.backend.domain.Ingredient;
import com.cofeapp.backend.domain.OptionItem;
import java.math.BigDecimal;

public record OptionItemResponse(
    String id,
    String name,
    BigDecimal priceDelta,
    String ingredientId,
    String ingredientName,
    String ingredientUnit,
    BigDecimal ingredientQuantity,
    boolean isDefault,
    boolean isAvailable,
    int position) {

  public static OptionItemResponse of(OptionItem item, Ingredient ingredient) {
    return new OptionItemResponse(
        item.getId(),
        item.getName(),
        item.getPriceDelta(),
        item.getIngredientId(),
        ingredient != null ? ingredient.getName() : null,
        ingredient != null ? ingredient.getUnit() : null,
        item.getIngredientQuantity(),
        Boolean.TRUE.equals(item.getIsDefault()),
        Boolean.TRUE.equals(item.getIsAvailable()),
        item.getPosition() != null ? item.getPosition() : 0);
  }
}
