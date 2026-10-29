package com.cofeapp.backend.table.dto;

import com.cofeapp.backend.domain.enums.TableStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Incluye {@code status} para poder marcar una mesa OCCUPIED/RESERVED a
 * mano desde este Bloque 1 (antes de que exista Orders). Desde el Bloque 3
 * en adelante, abrir/cerrar un pedido va a mover este campo automáticamente
 * — este endpoint sigue existiendo para el caso manual (ej. reservar una
 * mesa sin pedido todavía).
 */
@Getter
@Setter
public class UpdateTableRequest {

  @Min(1)
  private Integer number;

  @Size(max = 50)
  private String label;

  @Min(1)
  private Integer capacity;

  private TableStatus status;

  private Boolean isActive;
}
