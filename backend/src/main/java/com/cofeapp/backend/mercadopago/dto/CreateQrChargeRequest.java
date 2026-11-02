package com.cofeapp.backend.mercadopago.dto;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** {@code amount} opcional: si no viene, se cobra todo lo que falta del pedido. */
@Getter
@Setter
public class CreateQrChargeRequest {

  @DecimalMin(value = "0.01", inclusive = true, message = "amount tiene que ser mayor a 0.")
  private BigDecimal amount;

  /** V14 — cobrar "por productos": qué paga esta persona (el monto lo calcula el backend). */
  @jakarta.validation.Valid
  @jakarta.validation.constraints.Size(max = 100)
  private java.util.List<com.cofeapp.backend.order.dto.PaymentItemInput> items;
}
