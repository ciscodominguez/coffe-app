package com.cofeapp.backend.location.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code isActive} queda afuera a propósito: dar de baja un local implica
 * decidir qué pasa con sus mesas/pedidos activos, lo cual excede el
 * alcance de "operar con un solo local" de la Etapa 1 — se resuelve en la
 * Etapa 5 junto con el resto de multi-sucursal.
 */
@Getter
@Setter
public class UpdateLocationRequest {

  @Size(min = 1, max = 150)
  private String name;

  @Size(max = 255)
  private String address;

  @Size(max = 50)
  private String phone;

  /** 58 u 80 (mm) — ver {@code LocationService#update}, que rechaza cualquier otro valor. */
  private Integer receiptPaperWidthMm;
}
