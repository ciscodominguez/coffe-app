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
 * Una referencia dibujada en el plano (V18): la barra, la puerta, una
 * pared, una ventana, los baños, la cocina. No es una mesa: solo ayuda a
 * reconocer el lugar. Coordenadas en las unidades de la zona (100 x 75).
 *
 * <p>Entidad tenant-scoped.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "floor_items")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class FloorItem extends TenantScopedEntity {

  @Column(name = "area_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String areaId;

  /** BAR, DOOR, WALL, WINDOW, RESTROOM o KITCHEN. */
  @Column(nullable = false, length = 20)
  private String kind;

  @Column(length = 40)
  private String label;

  @Column(name = "pos_x", nullable = false, precision = 6, scale = 2)
  private BigDecimal x;

  @Column(name = "pos_y", nullable = false, precision = 6, scale = 2)
  private BigDecimal y;

  @Column(nullable = false, precision = 6, scale = 2)
  private BigDecimal width;

  @Column(nullable = false, precision = 6, scale = 2)
  private BigDecimal height;
}
