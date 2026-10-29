package com.cofeapp.backend.domain.enums;

/**
 * Estado de una mesa física. Espejo del CHECK {@code chk_tables_status} de
 * la migración ({@code V1__init_schema.sql}).
 *
 * <p>Por ahora se edita a mano vía {@code TableController#update} (Bloque
 * 1). A partir del Bloque 3 (Orders), abrir/cerrar un pedido sobre una mesa
 * va a mover este estado automáticamente — ver {@code TableService} cuando
 * llegue esa parte.
 */
public enum TableStatus {
  FREE,
  OCCUPIED,
  RESERVED,
}
