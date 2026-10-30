package com.cofeapp.backend.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateProductRequest {

  @NotBlank
  private String categoryId;

  @NotBlank
  @Size(min = 1, max = 150)
  private String name;

  @Size(max = 500)
  private String description;

  @Size(max = 1000)
  private String ingredients;

  @Size(max = 50)
  private String sku;

  @NotNull
  @DecimalMin(value = "0.0", inclusive = true, message = "price no puede ser negativo.")
  private BigDecimal price;

  @Size(max = 500)
  private String imageUrl;
}
