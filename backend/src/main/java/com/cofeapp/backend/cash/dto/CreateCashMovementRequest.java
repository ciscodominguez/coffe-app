package com.cofeapp.backend.cash.dto;

import com.cofeapp.backend.domain.enums.CashMovementType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCashMovementRequest {

  @NotNull
  private CashMovementType type;

  @NotNull
  @DecimalMin(value = "0.01", inclusive = true, message = "amount tiene que ser mayor a 0.")
  private BigDecimal amount;

  @NotBlank
  @Size(max = 255)
  private String reason;
}
