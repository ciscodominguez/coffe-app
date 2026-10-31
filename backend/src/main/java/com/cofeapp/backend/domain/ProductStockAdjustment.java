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
 * Auditoría de una corrección manual de {@link ProductStock} — mismo
 * criterio exacto que {@link IngredientStockAdjustment}, aplicado a
 * productos en modo {@code DIRECT} en vez de insumos. Ver ese Javadoc para
 * el detalle.
 *
 * <p>Entidad tenant-scoped: agrega acá {@code @Filter} (ver el Javadoc de
 * {@link TenantScopedEntity}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product_stock_adjustments")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class ProductStockAdjustment extends TenantScopedEntity {

  @Column(name = "product_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String productId;

  @Column(name = "location_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String locationId;

  @Column(name = "previous_quantity", nullable = false, precision = 12, scale = 3)
  private BigDecimal previousQuantity;

  @Column(name = "new_quantity", nullable = false, precision = 12, scale = 3)
  private BigDecimal newQuantity;

  @Column(nullable = false, precision = 12, scale = 3)
  private BigDecimal delta;

  @Column(length = 255)
  private String reason;

  @Column(name = "created_by_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String createdById;
}
