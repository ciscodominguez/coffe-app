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
 * Un descuento de la lista del negocio (V12, sobre la tabla `discounts` de
 * la V1): "Jubilados 10%", "Cortesía $500". Se elige con un toque al
 * cobrar. {@code requiresAuthorization}: solo supervisor, administrador o
 * dueño lo pueden aplicar.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "discounts")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class Discount extends TenantScopedEntity {

  @Column(nullable = false, length = 150)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DiscountType type;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal value;

  @Column(name = "requires_authorization", nullable = false)
  private Boolean requiresAuthorization = false;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;
}
