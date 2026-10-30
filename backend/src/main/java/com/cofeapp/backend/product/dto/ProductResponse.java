package com.cofeapp.backend.product.dto;

import com.cofeapp.backend.domain.Product;
import com.cofeapp.backend.domain.enums.StockMode;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code stockMode}/{@code minStock} — Bloque D (ítems 12/13), espejo
 * directo de las columnas del mismo nombre en {@code Product}. La receta
 * (solo relevante en modo {@code RECIPE}) NO viaja acá — se consulta
 * aparte vía {@code GET /stock/products/{id}/recipe} solo cuando hace
 * falta editarla, para no pagar ese join en cada listado del catálogo (ver
 * el Javadoc de {@code StockService}).
 */
public record ProductResponse(
    String id,
    String categoryId,
    String name,
    String description,
    String ingredients,
    String sku,
    BigDecimal price,
    String imageUrl,
    boolean isActive,
    StockMode stockMode,
    BigDecimal minStock,
    Instant createdAt,
    Instant updatedAt) {

  public static ProductResponse from(Product product) {
    return new ProductResponse(
        product.getId(),
        product.getCategoryId(),
        product.getName(),
        product.getDescription(),
        product.getIngredients(),
        product.getSku(),
        product.getPrice(),
        product.getImageUrl(),
        Boolean.TRUE.equals(product.getIsActive()),
        product.getStockMode(),
        product.getMinStock(),
        product.getCreatedAt(),
        product.getUpdatedAt());
  }
}
