package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.TenantFilters;
import com.cofeapp.backend.common.tenant.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * Categoría del catálogo (ej. "Cafés", "Panificados"). Etapa 2.
 *
 * <p>Entidad tenant-scoped: agrega acá {@code @Filter} (ver el Javadoc de
 * {@link TenantScopedEntity} sobre por qué esto se repite en cada entidad
 * concreta en vez de heredarse implícitamente). El {@code @FilterDef} ya NO
 * se repite acá — vive una sola vez en la superclase desde que se resolvió
 * el choque de Hibernate 6 con filtros duplicados en la Etapa 1.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "categories")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class Category extends TenantScopedEntity {

  @Column(nullable = false, length = 150)
  private String name;

  @Column(length = 500)
  private String description;

  @Column(name = "display_order", nullable = false)
  private Integer displayOrder = 0;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  @PrePersist
  private void defaults() {
    if (displayOrder == null) displayOrder = 0;
    if (isActive == null) isActive = true;
  }
}
