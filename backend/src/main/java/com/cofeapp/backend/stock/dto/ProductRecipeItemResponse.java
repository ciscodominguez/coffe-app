package com.cofeapp.backend.stock.dto;

import com.cofeapp.backend.domain.Ingredient;
import com.cofeapp.backend.domain.ProductRecipeItem;
import java.math.BigDecimal;

public record ProductRecipeItemResponse(String ingredientId, String ingredientName, String unit, BigDecimal quantityPerUnit) {

  public static ProductRecipeItemResponse of(ProductRecipeItem item, Ingredient ingredient) {
    return new ProductRecipeItemResponse(ingredient.getId(), ingredient.getName(), ingredient.getUnit(), item.getQuantityPerUnit());
  }
}
