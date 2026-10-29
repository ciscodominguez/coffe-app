package com.cofeapp.backend.table.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code locationId} es obligatorio en el body a propósito, aunque hoy cada
 * negocio tenga un solo local — mismo criterio "listo para multi-sucursal
 * desde el día uno" que el resto del esquema (evita tener que tocar este
 * contrato en la Etapa 5). El service igual valida que el local pertenezca
 * al negocio del token.
 */
@Getter
@Setter
public class CreateTableRequest {

  @NotBlank
  private String locationId;

  @NotNull
  @Min(1)
  private Integer number;

  @Size(max = 50)
  private String label;

  @Min(1)
  private Integer capacity;
}
