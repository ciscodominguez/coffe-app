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
 * Una línea de receta: cuánto de un {@link Ingredient} consume UNA unidad
 * de un {@link Product} en modo {@code RECIPE} (ver {@code StockMode}) —
 * Bloque D. Ej. "1 café cortado consume 18g de café en grano" sería una
 * fila con {@code quantityPerUnit = 18}.
 *
 * <p>Se reemplaza completa cada vez que se guarda la receta de un producto
 * (ver {@code StockService#replaceRecipe}) — se borran todas las filas
 * viejas de ese producto y se insertan las nuevas, nunca un PATCH línea
 * por línea: más simple para el frontend (un solo formulario con toda la
 * receta) y evita dejar líneas huérfanas si se saca un insumo.
 *
 * <p>Entidad tenant-scoped: agrega acá {@code @Filter} (ver el Javadoc de
 * {@link TenantScopedEntity}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product_recipe_items")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class ProductRecipeItem extends TenantScopedEntity {

  @Column(name = "product_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String productId;

  @Column(name = "ingredient_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String ingredientId;

  @Column(name = "quantity_per_unit", nullable = false, precision = 12, scale = 3)
  private BigDecimal quantityPerUnit;
}
