package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.TenantFilters;
import com.cofeapp.backend.common.tenant.TenantScopedEntity;
import com.cofeapp.backend.domain.enums.DiscountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * Un descuento aplicado a un pedido (V12): a un ítem ({@code orderItemId})
 * o a todo el pedido ({@code orderItemId} nulo). Nombre, tipo y valor se
 * copian al aplicarlo; {@code amount} es lo que descuenta de verdad y se
 * recalcula si el pedido cambia (por ejemplo, un 10% sobre todo el pedido
 * cuando se le agregan productos).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "order_discounts")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class OrderDiscount extends TenantScopedEntity {

  @Column(name = "order_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String orderId;

  @Column(name = "order_item_id", length = 36, columnDefinition = "CHAR(36)")
  private String orderItemId;

  @Column(name = "discount_id", length = 36, columnDefinition = "CHAR(36)")
  private String discountId;

  @Column(nullable = false, length = 150)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DiscountType type;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal value;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal amount = BigDecimal.ZERO;

  @Column(name = "supervisor_only", nullable = false)
  private Boolean supervisorOnly = false;

  @Column(length = 255)
  private String reason;

  @Column(name = "created_by_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String createdById;
}
