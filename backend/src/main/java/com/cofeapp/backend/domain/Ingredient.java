package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.TenantFilters;
import com.cofeapp.backend.common.tenant.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * Un insumo del negocio (ej. "Café en grano", "Leche") — Bloque D (ítems
 * 12/13). Es la DEFINICIÓN, compartida por todos los locales del negocio
 * (mismo criterio que {@link Category}/{@link Product}: el catálogo es del
 * negocio, no de un local puntual) — la cantidad ACTUAL de cada insumo
 * vive aparte, por local, en {@link IngredientStock}, porque el stock
 * físico sí es de cada local (mismo criterio que {@code CashSession}).
 *
 * <p>{@code unit} es texto libre corto ("g", "kg", "ml", "l", "unidad") en
 * vez de un enum — a propósito, para no tener que tocar código cada vez
 * que Fran necesite una unidad nueva; ver el mismo criterio en
 * {@code Product#ingredients}.
 *
 * <p>{@code minStock} es el umbral de "stock bajo" — un solo valor por
 * insumo, vale para cualquier local (no uno distinto por local, a
 * propósito: mantiene la configuración simple). Nulo = sin alerta
 * configurada para ese insumo.
 *
 * <p>Sin DELETE físico — {@code isActive} (mismo patrón que
 * {@link Product#isActive}): un insumo ya usado en una receta
 * ({@link ProductRecipeItem}) no puede desaparecer sin romper esa receta.
 *
 * <p>Entidad tenant-scoped: agrega acá {@code @Filter} (ver el Javadoc de
 * {@link TenantScopedEntity}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ingredients")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class Ingredient extends TenantScopedEntity {

  @Column(nullable = false, length = 150)
  private String name;

  @Column(nullable = false, length = 20)
  private String unit;

  @Column(name = "min_stock", precision = 12, scale = 3)
  private BigDecimal minStock;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  @PrePersist
  private void defaults() {
    if (isActive == null) isActive = true;
  }
}
