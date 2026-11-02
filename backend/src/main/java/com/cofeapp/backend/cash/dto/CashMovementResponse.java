package com.cofeapp.backend.cash.dto;

import com.cofeapp.backend.domain.CashMovement;
import com.cofeapp.backend.domain.enums.CashMovementType;
import java.math.BigDecimal;
import java.time.Instant;

/** {@code createdByName} — mejora "nombres reales" pedida por Fran, ver el mismo comentario en {@code OrderPaymentResponse}. */
public record CashMovementResponse(
    String id,
    String cashSessionId,
    CashMovementType type,
    BigDecimal amount,
    String reason,
    String createdById,
    String createdByName,
    Instant createdAt) {

  public static CashMovementResponse from(CashMovement movement, String createdByName) {
    return new CashMovementResponse(
        movement.getId(),
        movement.getCashSessionId(),
        movement.getType(),
        movement.getAmount(),
        movement.getReason(),
        movement.getCreatedById(),
        createdByName,
        movement.getCreatedAt());
  }
}
