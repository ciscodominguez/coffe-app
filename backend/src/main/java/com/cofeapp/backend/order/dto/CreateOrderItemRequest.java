package com.cofeapp.backend.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Sin {@code unitPrice}/{@code productName} a propósito: el precio y el
 * nombre se congelan del lado del servidor (ver {@code
 * OrderService#buildItem}), leyendo el {@link com.cofeapp.backend.domain.Product}
 * real — nunca se confía en lo que mande el cliente para esto.
 */
@Getter
@Setter
public class CreateOrderItemRequest {

  @NotBlank
  private String productId;

  @NotNull
  @Min(1)
  private Integer quantity;

  @Size(max = 500)
  private String notes;

  /**
   * V11 — ids de las opciones elegidas (tamaño, leche, extras…). El extra de
   * precio y lo que descuentan de stock salen de las opciones guardadas,
   * nunca del cliente — ver {@code OptionSelector}.
   */
  @Size(max = 30)
  private List<String> optionIds;
}
