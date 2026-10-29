package com.cofeapp.backend.location.dto;

import com.cofeapp.backend.domain.Location;
import java.time.Instant;

public record LocationResponse(
    String id,
    String name,
    String address,
    String phone,
    int receiptPaperWidthMm,
    boolean isActive,
    Instant createdAt,
    Instant updatedAt) {

  public static LocationResponse from(Location location) {
    return new LocationResponse(
        location.getId(),
        location.getName(),
        location.getAddress(),
        location.getPhone(),
        location.getReceiptPaperWidthMm() != null ? location.getReceiptPaperWidthMm() : 80,
        Boolean.TRUE.equals(location.getIsActive()),
        location.getCreatedAt(),
        location.getUpdatedAt());
  }
}
