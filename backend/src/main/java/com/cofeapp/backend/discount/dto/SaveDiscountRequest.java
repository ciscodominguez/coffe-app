package com.cofeapp.backend.discount.dto;

import com.cofeapp.backend.domain.enums.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Crear o editar un descuento de la lista (se manda completo, como el formulario). */
@Getter
@Setter
public class SaveDiscountRequest {

  @NotBlank
  @Size(max = 150)
  private String name;

  @NotNull
  private DiscountType type;

  @NotNull
  @DecimalMin(value = "0", inclusive = false)
  private BigDecimal value;

  /** Solo supervisor, administrador o dueño lo pueden aplicar. */
  private Boolean requiresAuthorization;

  /** Nulo = activo. */
  private Boolean isActive;
}
