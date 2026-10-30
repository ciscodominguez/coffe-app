package com.cofeapp.backend.category.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Todos los campos opcionales — mismo criterio de "patch parcial" que
 * {@code UpdateLocationRequest}. A diferencia de Location, acá
 * {@code isActive} sí se puede tocar: dar de baja una categoría es una baja
 * lógica simple, sin las complicaciones de multi-sucursal que tiene un
 * local.
 */
@Getter
@Setter
public class UpdateCategoryRequest {

  @Size(min = 1, max = 150)
  private String name;

  @Size(max = 500)
  private String description;

  private Integer displayOrder;

  private Boolean isActive;
}
