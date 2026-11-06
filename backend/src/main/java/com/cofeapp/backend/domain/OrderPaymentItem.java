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
 * Qué productos pagó un cobro (V14 — dividir la cuenta por lo que consumió
 * cada uno): {@code quantity} unidades del renglón {@code orderItemId}, que
 * suman {@code amount} de ese cobro.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "order_payment_items")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class OrderPaymentItem extends TenantScopedEntity {

  @Column(name = "order_payment_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String orderPaymentId;

  @Column(name = "order_item_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String orderItemId;

  @Column(nullable = false)
  private Integer quantity;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal amount;
}
