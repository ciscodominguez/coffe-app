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

/**
 * Una zona del plano del salón de un local (V18): "Salón", "Vereda",
 * "Planta alta"… Cada zona es un lienzo de 100 x 75 unidades donde se
 * ubican las mesas ({@link RestaurantTable#getAreaId()}) y las referencias
 * ({@link FloorItem}).
 *
 * <p>Entidad tenant-scoped.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "floor_areas")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class FloorArea extends TenantScopedEntity {

  @Column(name = "location_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String locationId;

  @Column(nullable = false, length = 50)
  private String name;

  /** Orden de las pestañas. */
  @Column(nullable = false)
  private Integer position = 0;
}
