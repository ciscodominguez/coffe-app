package com.cofeapp.backend.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Productos para sumar a un pedido abierto — ver {@code
 * OrderService#addItems}. Cada línea es igual que al crear el pedido
 * (producto, cantidad, nota): el precio y el nombre los congela el backend.
 */
@Getter
@Setter
public class AddOrderItemsRequest {

  @NotEmpty
  @Valid
  private List<CreateOrderItemRequest> items;
}
