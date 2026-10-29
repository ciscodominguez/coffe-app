package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.TenantFilters;
import com.cofeapp.backend.common.tenant.TenantScopedEntity;
import com.cofeapp.backend.domain.enums.OrderStatus;
import com.cofeapp.backend.domain.enums.SalesChannelKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * Un pedido. Etapa 2 (el corazón de la etapa).
 *
 * <p>{@code tableId} es nulo para un pedido de mostrador/takeaway sin mesa
 * asociada (el esquema ya lo soporta desde la Etapa 1). {@code
 * idempotencyKey} + {@code clientTimestamp} existen para la cola offline
 * del frontend de Meseras (prevista en el roadmap) — ver
 * {@code OrderService#create} sobre cómo se usan hoy, incluso sin cola
 * offline todavía del lado del frontend Java/Angular.
 *
 * <p>Sin campo {@code paymentMethod} a propósito (existía como columna
 * suelta hasta la V4, sin uso real): el cobro de un pedido ahora vive en
 * {@link OrderPayment}, una fila por cada línea de pago — ver el Javadoc
 * de esa clase sobre el pago dividido entre métodos (ítems 5/11).
 *
 * <p>Entidad tenant-scoped: agrega acá {@code @Filter} (ver el Javadoc de
 * {@link TenantScopedEntity}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "orders")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class Order extends TenantScopedEntity {

  @Column(name = "location_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String locationId;

  /** Nulo = pedido de mostrador/takeaway, sin mesa asociada. */
  @Column(name = "table_id", length = 36, columnDefinition = "CHAR(36)")
  private String tableId;

  @Column(name = "created_by_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String createdById;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OrderStatus status = OrderStatus.PENDING;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal subtotal = BigDecimal.ZERO;

  @Column(name = "discount_total", nullable = false, precision = 10, scale = 2)
  private BigDecimal discountTotal = BigDecimal.ZERO;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal total = BigDecimal.ZERO;

  @Column(length = 1000)
  private String notes;

  @Column(name = "idempotency_key", nullable = false, length = 100)
  private String idempotencyKey;

  @Column(name = "client_timestamp", nullable = false)
  private Instant clientTimestamp;

  @Column(name = "closed_at")
  private Instant closedAt;

  // ---- V19: pedido de una app de delivery (todo nulo en un pedido común) ----

  /** Canal de venta ({@link SalesChannel}) del que vino. */
  @Column(name = "channel_id", length = 36, columnDefinition = "CHAR(36)")
  private String channelId;

  /** Copia del tipo y el nombre del canal al cargar el pedido. */
  @Enumerated(EnumType.STRING)
  @Column(name = "channel_kind", length = 20)
  private SalesChannelKind channelKind;

  @Column(name = "channel_name", length = 40)
  private String channelName;

  /** Número del pedido en la app ("#1234"). No se puede repetir en el mismo canal. */
  @Column(name = "channel_order_ref", length = 40)
  private String channelOrderRef;

  @Column(name = "channel_customer", length = 80)
  private String channelCustomer;

  /** A qué hora pasa el repartidor (opcional). */
  @Column(name = "channel_pickup_at")
  private Instant channelPickupAt;

  /** true = lo cobró la app (queda "a cobrar de la app"); false = lo cobra el café. */
  @Column(name = "channel_paid_by_app")
  private Boolean channelPaidByApp;

  /** Comisión de la app al momento del pedido, en %. */
  @Column(name = "channel_commission_pct", precision = 5, scale = 2)
  private BigDecimal channelCommissionPct;

  public boolean isFromChannel() {
    return channelId != null;
  }

  @PrePersist
  private void defaults() {
    if (status == null) status = OrderStatus.PENDING;
    if (subtotal == null) subtotal = BigDecimal.ZERO;
    if (discountTotal == null) discountTotal = BigDecimal.ZERO;
    if (total == null) total = BigDecimal.ZERO;
  }
}
