package com.cofeapp.backend.order.dto;

import com.cofeapp.backend.domain.OrderItemOption;
import java.math.BigDecimal;

/** Una opción elegida en un ítem del pedido (V11), tal como se copió al venderlo. */
public record OrderItemOptionResponse(String optionId, String groupName, String name, BigDecimal priceDelta) {

  public static OrderItemOptionResponse from(OrderItemOption option) {
    return new OrderItemOptionResponse(
        option.getOptionId(), option.getGroupName(), option.getOptionName(), option.getPriceDelta());
  }
}
