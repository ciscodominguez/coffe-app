package com.cofeapp.backend.business.dto;

import com.cofeapp.backend.domain.Business;
import java.time.Instant;

public record BusinessResponse(
    String id,
    String legalName,
    String tradeName,
    String taxId,
    String fiscalCondition,
    String plan,
    String status,
    Instant createdAt,
    Instant updatedAt) {

  public static BusinessResponse from(Business business) {
    return new BusinessResponse(
        business.getId(),
        business.getLegalName(),
        business.getTradeName(),
        business.getTaxId(),
        business.getFiscalCondition().name(),
        business.getPlan().name(),
        business.getStatus().name(),
        business.getCreatedAt(),
        business.getUpdatedAt());
  }
}
