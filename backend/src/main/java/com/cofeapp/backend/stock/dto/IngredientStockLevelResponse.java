package com.cofeapp.backend.stock.dto;

import com.cofeapp.backend.domain.Ingredient;
import java.math.BigDecimal;

/**
 * Cantidad actual de un insumo en UN local puntual — lo que consume la
 * pantalla de consulta de stock (abierta a todos los roles, ver {@code
 * StockController}). {@code currentStock} es 0 cuando el insumo todavía no
 * tiene ninguna fila de {@code IngredientStock} en ese local (nunca se
 * tocó ahí) — nunca un error, ver {@code StockService#ingredientLevels}.
 */
public record IngredientStockLevelResponse(
    String ingredientId,
    String ingredientName,
    String unit,
    BigDecimal minStock,
    BigDecimal currentStock,
    boolean belowMinimum) {

  public static IngredientStockLevelResponse of(Ingredient ingredient, BigDecimal currentStock) {
    boolean belowMinimum = ingredient.getMinStock() != null && currentStock.compareTo(ingredient.getMinStock()) < 0;
    return new IngredientStockLevelResponse(
        ingredient.getId(), ingredient.getName(), ingredient.getUnit(), ingredient.getMinStock(), currentStock, belowMinimum);
  }
}
