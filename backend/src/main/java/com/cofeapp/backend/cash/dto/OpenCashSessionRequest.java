package com.cofeapp.backend.cash.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpenCashSessionRequest {

  @NotBlank
  private String locationId;

  /** Fondo inicial de caja (el efectivo con el que arranca el turno). */
  @NotNull
  @DecimalMin(value = "0.0", inclusive = true, message = "openingAmount no puede ser negativo.")
  private BigDecimal openingAmount;

  @Size(max = 1000)
  private String notes;
}
