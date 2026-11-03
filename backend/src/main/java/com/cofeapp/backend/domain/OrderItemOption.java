package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.TenantFilters;
import com.cofeapp.backend.common.tenant.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * Una opción elegida en un ítem de pedido (V11). Todo se COPIA al momento
 * de la venta — nombre del grupo y de la opción, precio extra, insumo y
 * cantidad — igual que {@link OrderItem} copia nombre y precio del
 * producto: editar o borrar la opción después no cambia pedidos viejos, y
 * cancelar un pedido repone exactamente lo que se descontó.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "order_item_options")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class OrderItemOption extends TenantScopedEntity {

  @Column(name = "order_item_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String orderItemId;

  @Column(name = "option_id", length = 36, columnDefinition = "CHAR(36)")
  private String optionId;

  @Column(name = "group_name", nullable = false, length = 100)
  private String groupName;

  @Column(name = "option_name", nullable = false, length = 100)
  private String optionName;

  @Column(name = "price_delta", nullable = false, precision = 10, scale = 2)
  private BigDecimal priceDelta = BigDecimal.ZERO;

  @Column(name = "ingredient_id", length = 36, columnDefinition = "CHAR(36)")
  private String ingredientId;

  @Column(name = "ingredient_quantity", precision = 12, scale = 3)
  private BigDecimal ingredientQuantity;

  @Column(nullable = false)
  private Integer position = 0;
}
