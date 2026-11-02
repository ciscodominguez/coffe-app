package com.cofeapp.backend.order.dto;

import com.cofeapp.backend.domain.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Una línea de cobro (ítems 5/11: pago dividido entre métodos — para cobrar
 * "mitad efectivo, mitad tarjeta" se manda un {@code POST
 * /orders/{id}/payments} por cada método, ver {@code OrderController}).
 */
@Getter
@Setter
public class CreateOrderPaymentRequest {

  @NotNull
  private PaymentMethod paymentMethod;

  /**
   * Monto a cobrar. Obligatorio salvo que venga {@code items}: en ese caso
   * lo calcula el backend con los productos elegidos.
   */
  @DecimalMin(value = "0.01", inclusive = true, message = "amount tiene que ser mayor a 0.")
  private java.math.BigDecimal amount;

  /**
   * V14 — dividir la cuenta "por lo que consumió cada uno": qué productos
   * (y cuántas unidades) paga este cobro. Quedan marcados como pagados.
   */
  @jakarta.validation.Valid
  @jakarta.validation.constraints.Size(max = 100)
  private java.util.List<PaymentItemInput> items;
}
