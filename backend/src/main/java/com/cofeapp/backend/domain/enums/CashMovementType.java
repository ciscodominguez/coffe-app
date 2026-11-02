package com.cofeapp.backend.domain.enums;

/**
 * Tipo de un movimiento manual de caja. Espejo del CHECK {@code
 * chk_cash_movements_type} de la migración ({@code
 * V4__cash_management.sql}).
 *
 * <p>Ítem 14 de las mejoras pedidas ("gestión de la caja"): un movimiento
 * es SIEMPRE en efectivo (un ingreso o egreso de dinero físico del cajón —
 * ej. "retiro para pagar al proveedor" o "se agregó cambio") y es distinto
 * de un cobro de pedido ({@code OrderPayment}), que puede ser en cualquier
 * método. {@link #INCOME}/{@link #EXPENSE} deciden el signo del monto (que
 * en la tabla siempre se guarda positivo) al calcular el efectivo esperado
 * del turno — ver {@code CashSessionService#computeExpectedCash}.
 */
public enum CashMovementType {
  INCOME,
  EXPENSE,
}
