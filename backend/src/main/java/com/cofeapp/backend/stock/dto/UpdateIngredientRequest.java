package com.cofeapp.backend.stock.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Todos los campos opcionales — mismo criterio de "patch parcial" que el resto del catálogo. */
@Getter
@Setter
public class UpdateIngredientRequest {

  @Size(min = 1, max = 150)
  private String name;

  @Size(min = 1, max = 20)
  private String unit;

  @DecimalMin(value = "0.0", inclusive = true, message = "minStock no puede ser negativo.")
  private BigDecimal minStock;

  private Boolean isActive;
}
