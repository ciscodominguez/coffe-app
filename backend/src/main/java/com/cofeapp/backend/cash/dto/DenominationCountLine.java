package com.cofeapp.backend.cash.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Una línea del conteo de efectivo por billete al cerrar un turno — ver el
 * Javadoc de {@code CashSessionDenomination}. {@code denomination} se
 * valida contra la lista de billetes vigentes en {@code
 * CashSessionService#assertKnownDenomination} (no con una anotación acá:
 * el mensaje de error necesita listar los valores válidos).
 */
@Getter
@Setter
public class DenominationCountLine {

  @NotNull private BigDecimal denomination;

  @NotNull
  @Min(value = 0, message = "quantity no puede ser negativo.")
  private Integer quantity;
}
