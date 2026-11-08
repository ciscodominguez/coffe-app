package com.cofeapp.backend.floor.dto;

import com.cofeapp.backend.table.dto.TableResponse;
import java.math.BigDecimal;
import java.util.List;

/**
 * El plano de un local (V18): sus zonas, las referencias dibujadas y todas
 * sus mesas (con su lugar en el plano, o sin ubicar).
 */
public record FloorPlanResponse(List<Area> areas, List<Item> items, List<TableResponse> tables) {

  public record Area(String id, String name, int position) {}

  public record Item(String id, String areaId, String kind, String label, BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height) {}
}
