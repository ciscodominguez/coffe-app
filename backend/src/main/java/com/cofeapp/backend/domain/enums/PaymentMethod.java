package com.cofeapp.backend.domain.enums;

/**
 * Espejo del CHECK {@code chk_order_payments_method} de la migración
 * ({@code V4__cash_management.sql}). El campo existía en el esquema desde
 * la Etapa 1 como una columna suelta en {@code orders} (ver el Javadoc de
 * esa migración sobre por qué se reemplazó por la tabla {@code
 * order_payments}) — acá se amplía de 4 a 6 valores para separar débito de
 * crédito (útil para reportes/KPI del ítem 9) y dejar un valor de fallback
 * ({@code OTHER}) para lo que no encaje (vales, cuenta corriente, etc.).
 *
 * <p>Ítem 5/11 de las mejoras pedidas: "agregar método de pago" + "permitir
 * abono en dos etapas" (interpretado como pago dividido entre métodos, no
 * como seña/saldo — ver el Javadoc de {@link com.cofeapp.backend.domain.OrderPayment}).
 * Un pedido puede tener VARIAS filas de {@code order_payments}, cada una
 * con su propio método — así se cubre "mitad efectivo, mitad tarjeta" sin
 * necesitar un solo valor por pedido.
 */
public enum PaymentMethod {
  CASH,
  DEBIT_CARD,
  CREDIT_CARD,
  TRANSFER,
  QR,
  OTHER,
  /**
   * V19 — lo cobró una app de delivery (PedidosYa, Rappi…): no entra en la
   * caja del mostrador, queda "a cobrar de la app". Solo para pedidos de app.
   */
  APP,
}
