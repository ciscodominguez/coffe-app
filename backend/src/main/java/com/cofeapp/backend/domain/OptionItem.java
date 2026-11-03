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
 * Una opción dentro de un {@link OptionGroup} (V11) — ej. "Almendra" en
 * "Leche". {@code priceDelta} se suma al precio del producto; si tiene
 * {@code ingredientId}, cada unidad vendida con esta opción descuenta
 * {@code ingredientQuantity} de ese insumo (además de la receta del
 * producto, si la tiene).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "option_items")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class OptionItem extends TenantScopedEntity {

  @Column(name = "group_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String groupId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(name = "price_delta", nullable = false, precision = 10, scale = 2)
  private BigDecimal priceDelta = BigDecimal.ZERO;

  @Column(name = "ingredient_id", length = 36, columnDefinition = "CHAR(36)")
  private String ingredientId;

  @Column(name = "ingredient_quantity", precision = 12, scale = 3)
  private BigDecimal ingredientQuantity;

  /** Viene marcada de entrada al tomar el pedido. */
  @Column(name = "is_default", nullable = false)
  private Boolean isDefault = false;

  /** False = "no hay por hoy": no se ofrece al tomar pedidos. */
  @Column(name = "is_available", nullable = false)
  private Boolean isAvailable = true;

  @Column(nullable = false)
  private Integer position = 0;
}
