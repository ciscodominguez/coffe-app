package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.TenantFilters;
import com.cofeapp.backend.common.tenant.TenantScopedEntity;
import com.cofeapp.backend.domain.enums.TableShape;
import com.cofeapp.backend.domain.enums.TableStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * Mesa física de un local. Se llama {@code RestaurantTable} y no
 * {@code Table} para no colisionar con la anotación
 * {@code jakarta.persistence.Table} usada abajo — la tabla real en la base
 * sigue llamándose {@code tables} (ver {@code @Table(name = "tables")}).
 * Etapa 2.
 *
 * <p>{@code locationId} es un id "suelto" (String), mismo criterio que
 * {@code categoryId} en {@link Product} — ver ese Javadoc.
 *
 * <p>Entidad tenant-scoped: agrega acá {@code @Filter} (ver el Javadoc de
 * {@link TenantScopedEntity}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tables")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class RestaurantTable extends TenantScopedEntity {

  @Column(name = "location_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String locationId;

  @Column(nullable = false)
  private Integer number;

  @Column(length = 50)
  private String label;

  private Integer capacity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TableStatus status = TableStatus.FREE;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  /** V18 — zona del plano donde está (nula = sin ubicar en el plano). */
  @Column(name = "area_id", length = 36, columnDefinition = "CHAR(36)")
  private String areaId;

  /** V18 — lugar y tamaño en el plano, en unidades de la zona (100 x 75). */
  @Column(name = "pos_x", precision = 6, scale = 2)
  private BigDecimal x;

  @Column(name = "pos_y", precision = 6, scale = 2)
  private BigDecimal y;

  @Column(precision = 6, scale = 2)
  private BigDecimal width;

  @Column(precision = 6, scale = 2)
  private BigDecimal height;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private TableShape shape = TableShape.SQUARE;

  @PrePersist
  private void defaults() {
    if (shape == null) shape = TableShape.SQUARE;
    if (status == null) status = TableStatus.FREE;
    if (isActive == null) isActive = true;
  }
}
