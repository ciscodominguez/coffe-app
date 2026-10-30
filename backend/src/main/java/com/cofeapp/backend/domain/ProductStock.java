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
 * La cantidad ACTUAL de un {@link Product} en modo {@code DIRECT} (ver
 * {@code StockMode}) en UN local puntual — mismo criterio exacto que
 * {@link IngredientStock}, aplicado a "productos terminados" (medialunas,
 * sandwiches) en vez de insumos. Ver el Javadoc de {@link IngredientStock}
 * para el detalle de stock negativo permitido y la simplificación de
 * concurrencia aceptada — vale igual acá.
 *
 * <p>Entidad tenant-scoped: agrega acá {@code @Filter} (ver el Javadoc de
 * {@link TenantScopedEntity}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product_stocks")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class ProductStock extends TenantScopedEntity {

  @Column(name = "product_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String productId;

  @Column(name = "location_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String locationId;

  @Column(name = "current_stock", nullable = false, precision = 12, scale = 3)
  private BigDecimal currentStock = BigDecimal.ZERO;
}
