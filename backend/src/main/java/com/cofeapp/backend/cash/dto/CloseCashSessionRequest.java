package com.cofeapp.backend.cash.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CloseCashSessionRequest {

  /**
   * Efectivo contado a mano por quien cierra, se compara contra el
   * esperado (ver CashSessionResponse). Cuando se manda {@link
   * #denominationBreakdown}, este valor se IGNORA: el backend recalcula
   * {@code countedCashAmount} como la suma del conteo por billete, para que
   * el monto final nunca pueda desviarse de lo efectivamente contado (ver
   * {@code CashSessionService#close}). Sigue siendo obligatorio para no
   * romper un cierre "a la vieja", sin desglose.
   */
  @NotNull
  @DecimalMin(value = "0.0", inclusive = true, message = "countedCashAmount no puede ser negativo.")
  private BigDecimal countedCashAmount;

  /**
   * Conteo de efectivo por billete — opcional (un café que prefiera seguir
   * cargando un único total puede mandar esta lista vacía o nula). Cuando
   * viene con líneas, reemplaza a {@link #countedCashAmount} como fuente de
   * verdad — ver el Javadoc de arriba.
   */
  @Valid private List<DenominationCountLine> denominationBreakdown;

  /** Ítem 15: lote de Postnet vinculado a este cierre — un solo lote, ambos opcionales (no todo local cobra con Postnet). */
  @Size(max = 50)
  private String postnetBatchNumber;

  @DecimalMin(value = "0.0", inclusive = true, message = "postnetReportedAmount no puede ser negativo.")
  private BigDecimal postnetReportedAmount;

  @Size(max = 1000)
  private String notes;
}
