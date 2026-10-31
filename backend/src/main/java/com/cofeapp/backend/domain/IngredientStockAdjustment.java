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
 * Auditoría de una corrección manual de {@link IngredientStock} — Bloque
 * D, mismo patrón exacto que {@code CashMovement} para la Caja: quien
 * corrige cuenta lo que hay físicamente y carga el valor NUEVO (no un
 * delta a mano), el sistema calcula y guarda {@code delta} solo para
 * referencia rápida en el historial (ver {@code
 * StockService#adjustIngredientStock}).
 *
 * <p>Sin endpoint de edición/baja a propósito — es un registro contable,
 * mismo criterio que {@code CashMovement}/{@code OrderItem}: si se cargó
 * mal, se hace un ajuste nuevo que lo corrija, nunca se edita el original.
 *
 * <p>Entidad tenant-scoped: agrega acá {@code @Filter} (ver el Javadoc de
 * {@link TenantScopedEntity}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ingredient_stock_adjustments")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class IngredientStockAdjustment extends TenantScopedEntity {

  @Column(name = "ingredient_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String ingredientId;

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
