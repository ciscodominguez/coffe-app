package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.TenantFilters;
import com.cofeapp.backend.common.tenant.TenantScopedEntity;
import com.cofeapp.backend.domain.enums.StockMode;
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
 * Producto del catálogo (lo que se vende). Etapa 2.
 *
 * <p>{@code categoryId} es un id "suelto" (String, {@code CHAR(36)} —
 * mismo criterio de {@link com.cofeapp.backend.common.tenant.BaseEntity}
 * sobre por qué se fuerza {@code columnDefinition}), NO una relación JPA
 * {@code @ManyToOne}: igual que {@code locationId} en {@link User}, esto
 * obliga a que cada acceso a la categoría relacionada pase explícitamente
 * por {@code CategoryRepository#findByIdScoped} (que sí respeta el
 * aislamiento multi-tenant) en el código de servicio, en vez de dejar que
 * el fetching automático de una relación JPA cargue una fila sin pasar por
 * ese camino.
 *
 * <p>Entidad tenant-scoped: agrega acá {@code @Filter} (ver el Javadoc de
 * {@link TenantScopedEntity}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "products")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class Product extends TenantScopedEntity {

  @Column(name = "category_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String categoryId;

  @Column(nullable = false, length = 150)
  private String name;

  @Column(length = 500)
  private String description;

  /**
   * Texto libre, sin tabla aparte — ítem 10 de las mejoras pedidas,
   * deliberadamente "chiquito" (así se pidió): si en el futuro hace falta
   * una lista estructurada con alérgenos/cantidades, esa es una migración
   * aparte (ver V3__product_ingredients.sql).
   */
  @Column(length = 1000)
  private String ingredients;

  @Column(length = 50)
  private String sku;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal price;

  @Column(name = "image_url", length = 500)
  private String imageUrl;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  /**
   * Bloque D (ítems 12/13) — cómo este producto maneja su stock. Ver el
   * Javadoc de {@link StockMode}. Columna suelta acá (no una tabla de
   * configuración aparte) a propósito: es un único valor por producto, sin
   * historial ni variación por local — mismo criterio que
   * {@code ingredients} (texto libre en vez de tabla) más arriba.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "stock_mode", nullable = false, length = 10)
  private StockMode stockMode = StockMode.NONE;

  /**
   * Umbral de "stock bajo" que solo aplica en modo {@code DIRECT} — en
   * modo {@code RECIPE} el umbral vive en cada {@code Ingredient} de la
   * receta, no acá. Nulo = sin alerta configurada.
   */
  @Column(name = "min_stock", precision = 12, scale = 3)
  private BigDecimal minStock;

  @PrePersist
  private void defaults() {
    if (isActive == null) isActive = true;
    if (stockMode == null) stockMode = StockMode.NONE;
  }
}
