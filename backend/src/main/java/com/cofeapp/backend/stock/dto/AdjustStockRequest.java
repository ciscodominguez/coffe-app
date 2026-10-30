package com.cofeapp.backend.stock.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Ajuste manual de stock — Bloque D. Se manda la cantidad REAL contada
 * (no un delta a mano), mismo criterio que el conteo de efectivo por
 * billete de la Caja: quien corrige cuenta lo que hay y carga ese número,
 * el backend calcula el delta (ver {@code StockService}).
 */
@Getter
@Setter
public class AdjustStockRequest {

  @NotBlank
  private String locationId;

  /** Cantidad real, contada a mano — puede ser menor, igual o mayor a la que el sistema tenía calculada, nunca negativa (un conteo físico no lo es). */
  @NotNull
  @DecimalMin(value = "0.0", inclusive = true, message = "newQuantity no puede ser negativo.")
  private BigDecimal newQuantity;

  @Size(max = 255)
  private String reason;
}
