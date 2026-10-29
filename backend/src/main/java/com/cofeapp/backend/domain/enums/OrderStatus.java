package com.cofeapp.backend.domain.enums;

/**
 * Espejo del CHECK {@code chk_orders_status} de la migración. Máquina de
 * estados: PENDING → IN_PREPARATION → READY → DELIVERED, con CANCELLED
 * alcanzable desde cualquier estado no terminal. Ver
 * {@code OrderService#allowedRolesFor} para las transiciones válidas y el
 * rol habilitado para cada una.
 *
 * <p>Desde el Bloque C (caja): la transición a DELIVERED además exige que
 * el pedido esté cobrado por completo (suma de {@code OrderPayment} ≥
 * total) — ver {@code OrderService#assertFullyPaid}. Antes de este bloque
 * se podía entregar un pedido sin registrar ningún cobro; a partir de acá
 * hace falta cobrarlo (aunque sea dividido entre métodos) antes de poder
 * marcarlo como entregado.
 */
public enum OrderStatus {
  PENDING,
  IN_PREPARATION,
  READY,
  DELIVERED,
  CANCELLED,
}
