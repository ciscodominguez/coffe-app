package com.cofeapp.backend.stock.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateIngredientRequest {

  @NotBlank
  @Size(min = 1, max = 150)
  private String name;

  /** Texto libre corto — "g", "kg", "ml", "l", "unidad", etc. */
  @NotBlank
  @Size(min = 1, max = 20)
  private String unit;

  /** Umbral de "stock bajo" — opcional, nulo = sin alerta configurada. */
  @DecimalMin(value = "0.0", inclusive = true, message = "minStock no puede ser negativo.")
  private BigDecimal minStock;
}
