package com.cofeapp.backend.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** V14 — un renglón del pedido que paga este cobro, y cuántas unidades. */
@Getter
@Setter
public class PaymentItemInput {

  @NotBlank
  private String orderItemId;

  @NotNull
  @Min(1)
  private Integer quantity;
}
