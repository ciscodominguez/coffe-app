package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.TenantFilters;
import com.cofeapp.backend.common.tenant.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/** Qué grupos de opciones usa cada producto (V11). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product_option_groups")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class ProductOptionGroup extends TenantScopedEntity {

  @Column(name = "product_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String productId;

  @Column(name = "group_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String groupId;
}
