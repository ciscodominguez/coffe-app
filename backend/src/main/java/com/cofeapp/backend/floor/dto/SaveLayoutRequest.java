package com.cofeapp.backend.floor.dto;

import com.cofeapp.backend.domain.enums.TableShape;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Cómo quedó una zona del plano después de editarla (V18): qué mesas están
 * en la zona y dónde, y las referencias dibujadas. Las mesas que estaban en
 * esta zona y no vienen en la lista quedan "sin ubicar"; las referencias
 * que no vienen se borran.
 */
public record SaveLayoutRequest(
    @NotNull @Valid @Size(max = 200) List<TablePlacement> tables,
    @NotNull @Valid @Size(max = 100) List<ItemPlacement> items) {

  public record TablePlacement(
      @NotBlank String id,
      @NotNull BigDecimal x,
      @NotNull BigDecimal y,
      @NotNull BigDecimal width,
      @NotNull BigDecimal height,
      @NotNull TableShape shape) {}

  public record ItemPlacement(
      String id,
      @NotBlank @Pattern(regexp = "BAR|DOOR|WALL|WINDOW|RESTROOM|KITCHEN", message = "Tipo de referencia inválido.") String kind,
      @Size(max = 40) String label,
      @NotNull BigDecimal x,
      @NotNull BigDecimal y,
      @NotNull BigDecimal width,
      @NotNull BigDecimal height) {}
}
