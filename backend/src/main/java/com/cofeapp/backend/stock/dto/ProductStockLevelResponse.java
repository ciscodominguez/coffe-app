package com.cofeapp.backend.stock.dto;

import com.cofeapp.backend.domain.Product;
import java.math.BigDecimal;

/** Cantidad actual de un producto en modo {@code DIRECT} en UN local puntual — mismo criterio que {@code IngredientStockLevelResponse}. */
public record ProductStockLevelResponse(
    String productId,
    String productName,
    BigDecimal minStock,
    BigDecimal currentStock,
    boolean belowMinimum) {

  public static ProductStockLevelResponse of(Product product, BigDecimal currentStock) {
    boolean belowMinimum = product.getMinStock() != null && currentStock.compareTo(product.getMinStock()) < 0;
    return new ProductStockLevelResponse(product.getId(), product.getName(), product.getMinStock(), currentStock, belowMinimum);
  }
}
