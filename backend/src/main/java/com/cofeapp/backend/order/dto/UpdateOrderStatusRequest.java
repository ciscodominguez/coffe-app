package com.cofeapp.backend.order.dto;

import com.cofeapp.backend.domain.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateOrderStatusRequest {

  @NotNull
  private OrderStatus status;
}
