package com.cofeapp.backend.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCategoryRequest {

  @NotBlank
  @Size(min = 1, max = 150)
  private String name;

  @Size(max = 500)
  private String description;

  /** Nulo = se guarda como 0 (ver {@code Category#defaults}). */
  private Integer displayOrder;
}
