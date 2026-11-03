package com.cofeapp.backend.order.dto;

import com.cofeapp.backend.domain.enums.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Aplicar un descuento a un pedido abierto (V12).
 *
 * <ul>
 *   <li>{@code orderItemId}: a qué ítem; nulo = a todo el pedido.
 *   <li>De la lista: {@code discountId}.
 *   <li>A mano: {@code type} + {@code value} + {@code reason} (obligatorio).
 * </ul>
 *
 * Si ese ítem (o el pedido) ya tenía un descuento, se reemplaza.
 */
@Getter
@Setter
public class ApplyDiscountRequest {

  private String orderItemId;

  private String discountId;

  private DiscountType type;

  @DecimalMin(value = "0", inclusive = false)
  private BigDecimal value;

  @Size(max = 255)
  private String reason;
}
