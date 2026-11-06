package com.cofeapp.backend.channel.dto;

import com.cofeapp.backend.domain.SalesChannel;
import com.cofeapp.backend.domain.enums.SalesChannelKind;
import com.cofeapp.backend.domain.enums.SalesChannelMode;
import java.math.BigDecimal;

public record SalesChannelResponse(
    String id, String locationId, SalesChannelKind kind, String name, SalesChannelMode mode, BigDecimal commissionPct) {

  public static SalesChannelResponse from(SalesChannel c) {
    return new SalesChannelResponse(c.getId(), c.getLocationId(), c.getKind(), c.getName(), c.getMode(), c.getCommissionPct());
  }
}
