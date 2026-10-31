package com.cofeapp.backend.stock.dto;

import com.cofeapp.backend.domain.enums.StockMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Configura cómo un producto maneja su stock — Bloque D. {@code
 * recipeItems} solo se usa (y reemplaza la receta completa, ver
 * {@code StockService#replaceRecipe}) cuando {@code stockMode = RECIPE};
 * en {@code DIRECT}/{@code NONE} se ignora si viene.
 */
@Getter
@Setter
public class UpdateProductStockConfigRequest {

  @NotNull private StockMode stockMode;

  /** Solo aplica en modo DIRECT — ver el Javadoc de {@code Product#minStock}. */
  @DecimalMin(value = "0.0", inclusive = true, message = "minStock no puede ser negativo.")
  private BigDecimal minStock;

  @Valid private List<RecipeItemInput> recipeItems;
}
