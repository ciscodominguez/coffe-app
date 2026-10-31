package com.cofeapp.backend.stock.dto;

import com.cofeapp.backend.domain.IngredientStockAdjustment;
import com.cofeapp.backend.domain.ProductStockAdjustment;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un ajuste manual de stock ya aplicado — usada tanto para insumos como
 * para productos (las dos entidades de auditoría son estructuralmente
 * iguales, ver el Javadoc de {@code IngredientStockAdjustment}).
 * {@code createdByName} — mismo criterio "nombres reales" que la Caja.
 */
public record StockAdjustmentResponse(
    String id,
    String locationId,
    BigDecimal previousQuantity,
    BigDecimal newQuantity,
    BigDecimal delta,
    String reason,
    String createdById,
    String createdByName,
    Instant createdAt) {

  public static StockAdjustmentResponse from(IngredientStockAdjustment adjustment, String createdByName) {
    return new StockAdjustmentResponse(
        adjustment.getId(),
        adjustment.getLocationId(),
        adjustment.getPreviousQuantity(),
        adjustment.getNewQuantity(),
        adjustment.getDelta(),
        adjustment.getReason(),
        adjustment.getCreatedById(),
        createdByName,
        adjustment.getCreatedAt());
  }

  public static StockAdjustmentResponse from(ProductStockAdjustment adjustment, String createdByName) {
    return new StockAdjustmentResponse(
        adjustment.getId(),
        adjustment.getLocationId(),
        adjustment.getPreviousQuantity(),
        adjustment.getNewQuantity(),
        adjustment.getDelta(),
        adjustment.getReason(),
        adjustment.getCreatedById(),
        createdByName,
        adjustment.getCreatedAt());
  }
}
