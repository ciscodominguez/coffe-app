package com.cofeapp.backend.domain.enums;

/**
 * Estado de un turno de caja. Espejo del CHECK {@code
 * chk_cash_sessions_status} de la migración ({@code
 * V4__cash_management.sql}).
 *
 * <p>Solo dos estados a propósito: un turno se abre una vez y se cierra una
 * vez, sin pausas intermedias (si en algún momento hiciera falta modelar
 * una pausa, sería un tercer estado nuevo, no una reapertura de uno
 * CLOSED). Ver {@code CashSession} sobre cómo la migración garantiza a
 * nivel de base que nunca haya dos turnos OPEN a la vez para el mismo
 * local.
 */
public enum CashSessionStatus {
  OPEN,
  CLOSED,
}
