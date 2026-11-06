package com.cofeapp.backend.platform.dto;

import com.cofeapp.backend.domain.enums.BusinessPlan;
import com.cofeapp.backend.domain.enums.BusinessStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Cambios desde el panel (V15): solo viaja lo que cambia. */
@Getter
@Setter
public class UpdateBusinessStatusRequest {

  private BusinessStatus status;

  /** Motivo al suspender o dar de baja (lo ve el café al querer entrar). */
  @Size(max = 255)
  private String statusReason;

  private BusinessPlan plan;

  /** Notas internas (nunca las ve el café). */
  @Size(max = 2000)
  private String adminNotes;

  /** V16 — "Sin cobro" (cortesía): nunca se le cobra ni se suspende solo. */
  private Boolean billingExempt;

  /** V16 — sumar días de prueba gratis. */
  @Min(value = 1, message = "Sumá al menos 1 día.")
  @Max(value = 90, message = "Se pueden sumar hasta 90 días de una vez.")
  private Integer extendTrialDays;
}
