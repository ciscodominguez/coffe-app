package com.cofeapp.backend.option.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Una opción dentro del formulario de un grupo. Con {@code id}: se edita esa
 * opción; sin {@code id}: es nueva. Las opciones que ya existían y no vienen
 * en la lista se borran (los pedidos viejos conservan su copia).
 */
@Getter
@Setter
public class OptionItemInput {

  private String id;

  @NotBlank
  @Size(max = 100)
  private String name;

  @NotNull
  @DecimalMin("0")
  private BigDecimal priceDelta;

  /** Insumo que gasta (opcional). Si viene, también tiene que venir {@code ingredientQuantity}. */
  private String ingredientId;

  @DecimalMin(value = "0", inclusive = false)
  private BigDecimal ingredientQuantity;

  private Boolean isDefault;

  private Boolean isAvailable;
}
