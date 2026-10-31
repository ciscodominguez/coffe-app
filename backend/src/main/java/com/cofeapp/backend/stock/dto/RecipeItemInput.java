package com.cofeapp.backend.stock.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecipeItemInput {

  @NotBlank
  private String ingredientId;

  @NotNull
  @DecimalMin(value = "0.001", inclusive = true, message = "quantityPerUnit tiene que ser mayor a 0.")
  private BigDecimal quantityPerUnit;
}
