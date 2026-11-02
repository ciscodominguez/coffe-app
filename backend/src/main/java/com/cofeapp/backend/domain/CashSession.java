package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.TenantFilters;
import com.cofeapp.backend.common.tenant.TenantScopedEntity;
import com.cofeapp.backend.domain.enums.CashSessionStatus;
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
 * Un turno de caja — ítem 14 de las mejoras pedidas ("gestión de la caja:
 * facturación en efectivo, digital, etc."). Se abre con un fondo inicial
 * ({@code openingAmount}) y se cierra contando el efectivo físico ({@code
 * countedCashAmount}), comparado contra lo esperado (fondo + cobros en
 * efectivo + movimientos manuales — ver {@code CashSessionService}).
 *
 * <p>Roles: cualquiera de {@code FRONT_OF_HOUSE_ROLES} (WAITER incluido, no
 * solo gerencia) puede abrir/cerrar un turno y cargar movimientos — así lo
 * pidió Fran explícitamente ("todos los que atienden"), KITCHEN nunca (no
 * maneja caja). Ver {@code CashSessionController}.
 *
 * <p><b>Un solo turno abierto por local a la vez</b>, garantizado a nivel
 * de BASE (no solo de aplicación): la migración agrega una columna
 * generada {@code open_location_id} (= {@code location_id} mientras {@code
 * status = 'OPEN'}, NULL en cuanto se cierra) con un UNIQUE KEY sobre
 * {@code (business_id, open_location_id)}. Como MySQL no cuenta los NULL
 * como duplicados en un UNIQUE KEY, esto arma un "unique parcial" sin
 * necesitar el soporte de índices filtrados que sí tiene Postgres. Un
 * segundo intento de abrir turno para el mismo local cae en {@code
 * DataIntegrityViolationException} incluso si dos requests concurrentes
 * pasan el chequeo previo de "no hay turno abierto" al mismo tiempo — ver
 * {@code CashSessionService#open}, mismo criterio que la idempotencia de
 * {@code OrderService#create}.
 *
 * <p>Ítem 15 ("vincular los cierres de caja con los lotes de Postnet"):
 * {@code postnetBatchNumber}/{@code postnetReportedAmount} son un solo
 * lote por cierre (no una tabla aparte) — así lo pidió Fran, alcanza para
 * comparar el lote que tira la terminal contra lo que el sistema calculó
 * en pagos electrónicos (ver {@code CashSessionResponse}).
 *
 * <p>Entidad tenant-scoped: agrega acá {@code @Filter} (ver el Javadoc de
 * {@link TenantScopedEntity}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cash_sessions")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class CashSession extends TenantScopedEntity {

  @Column(name = "location_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String locationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private CashSessionStatus status = CashSessionStatus.OPEN;

  @Column(name = "opened_by_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String openedById;

  @Column(name = "opened_at", nullable = false)
  private Instant openedAt;

  @Column(name = "opening_amount", nullable = false, precision = 10, scale = 2)
  private BigDecimal openingAmount = BigDecimal.ZERO;

  @Column(name = "closed_by_id", length = 36, columnDefinition = "CHAR(36)")
  private String closedById;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "counted_cash_amount", precision = 10, scale = 2)
  private BigDecimal countedCashAmount;

  @Column(name = "postnet_batch_number", length = 50)
  private String postnetBatchNumber;

  @Column(name = "postnet_reported_amount", precision = 10, scale = 2)
  private BigDecimal postnetReportedAmount;

  @Column(length = 1000)
  private String notes;

  @PrePersist
  private void defaults() {
    if (status == null) status = CashSessionStatus.OPEN;
    if (openingAmount == null) openingAmount = BigDecimal.ZERO;
  }
}
