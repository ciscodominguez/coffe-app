package com.cofeapp.backend.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Todos los campos opcionales — mismo criterio de "patch parcial" que el resto del catálogo. */
@Getter
@Setter
public class UpdateProductRequest {

  private String categoryId;

  @Size(min = 1, max = 150)
  private String name;

  @Size(max = 500)
  private String description;

  @Size(max = 1000)
  private String ingredients;

  @Size(max = 50)
  private String sku;

  @DecimalMin(value = "0.0", inclusive = true, message = "price no puede ser negativo.")
  private BigDecimal price;

  @Size(max = 500)
  private String imageUrl;

  private Boolean isActive;
}
