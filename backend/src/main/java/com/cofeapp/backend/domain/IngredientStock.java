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
 * La cantidad ACTUAL de un {@link Ingredient} en UN local puntual — Bloque
 * D. Una fila por cada combinación (insumo, local) realmente usada, creada
 * "perezosamente" la primera vez que ese insumo se toca en ese local (ver
 * {@code StockService#getOrCreateIngredientStock}) — no se precargan todas
 * las combinaciones al crear un insumo o un local.
 *
 * <p>{@code currentStock} puede quedar NEGATIVO a propósito — Fran pidió
 * explícitamente no trabar una venta por falta de stock (evita frenar el
 * mostrador en la hora pico): se permite vender igual, y queda marcado
 * como alerta (ver {@code IngredientStockLevelResponse#belowMinimum}) para
 * corregir después con un ajuste manual (ver {@link
 * IngredientStockAdjustment}).
 *
 * <p><b>Simplificación aceptada</b> (documentada en el README): el
 * incremento/decremento no usa lock pesimista como {@code
 * TableRepository#findByIdScopedForUpdate} — dos ventas simultáneas del
 * mismo insumo en el mismo local podrían perder un update bajo carga muy
 * alta. Se aceptó el riesgo porque Fran ya planea corregir el conteo a
 * mano en cada apertura/cierre de local (mismo criterio que el conteo de
 * efectivo de la Caja), así que un desvío chico se autocorrige solo en la
 * próxima corrección manual — agregar el lock es una mejora futura si
 * llega a doler en el uso real.
 *
 * <p>Entidad tenant-scoped: agrega acá {@code @Filter} (ver el Javadoc de
 * {@link TenantScopedEntity}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ingredient_stocks")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class IngredientStock extends TenantScopedEntity {

  @Column(name = "ingredient_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String ingredientId;

  @Column(name = "location_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String locationId;

  @Column(name = "current_stock", nullable = false, precision = 12, scale = 3)
  private BigDecimal currentStock = BigDecimal.ZERO;
}
