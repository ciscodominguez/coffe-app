package com.cofeapp.backend.cash.dto;

import com.cofeapp.backend.domain.CashSessionDenomination;
import java.math.BigDecimal;

public record DenominationCountResponse(BigDecimal denomination, Integer quantity, BigDecimal subtotal) {

  public static DenominationCountResponse from(CashSessionDenomination line) {
    return new DenominationCountResponse(line.getDenomination(), line.getQuantity(), line.getSubtotal());
  }
}
