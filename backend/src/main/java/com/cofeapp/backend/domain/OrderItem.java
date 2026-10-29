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
 * Un ítem dentro de un pedido. Etapa 2.
 *
 * <p>{@code productName}/{@code unitPrice} son un SNAPSHOT tomado al
 * momento de la venta (ver {@code OrderService#buildItem}) — nunca se
 * vuelven a leer de {@link Product} después de creado el ítem, ni siquiera
 * si el producto cambia de precio o de nombre más adelante. Es la regla de
 * "precios de OrderItems inmutables al momento de la venta" del proyecto.
 *
 * <p>{@code discountId}/{@code discountAmount} existen en el esquema desde
 * la Etapa 1 pero quedan sin uso real por ahora — Discounts está diferido a
 * la Etapa 3, así que acá siempre quedan en nulo/0 (ver
 * {@code OrderService#buildItem}).
 *
 * <p>Sin endpoint de edición a propósito: un ítem ya creado no se
 * modifica — de ahí que la tabla {@code order_items} necesitara una
 * migración aparte (V2) solo para agregarle {@code updated_at} y así poder
 * seguir extendiendo {@link TenantScopedEntity} sin una jerarquía paralela
 * (ver el comentario de esa migración).
 *
 * <p>Entidad tenant-scoped: agrega acá {@code @Filter} (ver el Javadoc de
 * {@link TenantScopedEntity}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "order_items")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class OrderItem extends TenantScopedEntity {

  @Column(name = "order_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String orderId;

  @Column(name = "product_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String productId;

  @Column(name = "product_name", nullable = false, length = 150)
  private String productName;

  @Column(nullable = false)
  private Integer quantity;

  @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "discount_id", length = 36, columnDefinition = "CHAR(36)")
  private String discountId;

  @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
  private BigDecimal discountAmount = BigDecimal.ZERO;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal subtotal;

  @Column(length = 500)
  private String notes;

  /**
   * Tanda en la que entró este ítem (V8): 1 = con el pedido original; 2, 3…
   * = agregados después con {@code OrderService#addItems}. Cocina usa esto
   * para marcar lo recién agregado y atenuar lo que ya se había preparado.
   */
  @Column(nullable = false)
  private Integer batch = 1;

  /** V13 — renglón dentro del pedido (1, 2, 3…): orden fijo aunque dos ítems tengan el mismo {@code createdAt}. */
  @Column(name = "line_number", nullable = false)
  private Integer lineNumber = 0;

  @PrePersist
  private void defaults() {
    if (discountAmount == null) discountAmount = BigDecimal.ZERO;
    if (batch == null) batch = 1;
    if (lineNumber == null) lineNumber = 0;
  }
}
