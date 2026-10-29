package com.cofeapp.backend.table.dto;

import com.cofeapp.backend.domain.RestaurantTable;
import com.cofeapp.backend.domain.enums.TableStatus;
import com.cofeapp.backend.domain.enums.TableShape;
import java.math.BigDecimal;
import java.time.Instant;

public record TableResponse(
    String id,
    String locationId,
    int number,
    String label,
    Integer capacity,
    TableStatus status,
    boolean isActive,
    Instant createdAt,
    Instant updatedAt,
    // V18 — plano del salón (nulos = sin ubicar).
    String areaId,
    BigDecimal x,
    BigDecimal y,
    BigDecimal width,
    BigDecimal height,
    TableShape shape) {

  public static TableResponse from(RestaurantTable table) {
    return new TableResponse(
        table.getId(),
        table.getLocationId(),
        table.getNumber(),
        table.getLabel(),
        table.getCapacity(),
        table.getStatus(),
        Boolean.TRUE.equals(table.getIsActive()),
        table.getCreatedAt(),
        table.getUpdatedAt(),
        table.getAreaId(),
        table.getX(),
        table.getY(),
        table.getWidth(),
        table.getHeight(),
        table.getShape());
  }
}
