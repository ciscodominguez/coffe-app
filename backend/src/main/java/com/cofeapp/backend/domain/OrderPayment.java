package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.TenantFilters;
import com.cofeapp.backend.common.tenant.TenantScopedEntity;
import com.cofeapp.backend.domain.enums.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * Una línea de cobro de un pedido — ítems 5/11 de las mejoras pedidas
 * ("agregar método de pago" + "permitir abono en dos etapas", interpretado
 * como PAGO DIVIDIDO ENTRE MÉTODOS: un mismo pedido puede tener varias
 * filas acá, cada una con su propio {@link PaymentMethod} y monto — ej.
 * mitad efectivo, mitad tarjeta — a diferencia de una seña/saldo pagados en
 * momentos distintos, que Fran decidió no necesitar).
 *
 * <p>Reemplaza a la columna suelta {@code orders.payment_method} que
 * existía desde la Etapa 1 sin uso real (ver el Javadoc de {@code
 * V4__cash_management.sql} sobre por qué se dropeó: un pedido ahora puede
 * tener 0, 1 o varias líneas de pago, nunca un único valor).
 *
 * <p>{@code cashSessionId} es el turno de caja ABIERTO del local del
 * pedido al momento del cobro (ver {@code OrderService#addPayment}) — sin
 * turno abierto no se puede cobrar, es la forma en que la caja "sabe" de
 * dónde salió cada pago para totalizar el cierre (ítem 14).
 *
 * <p>Sin endpoint de edición/baja a propósito: un cobro ya registrado no se
 * modifica — mismo criterio de solo-alta que {@link OrderItem}/{@link
 * CashMovement}.
 *
 * <p>Entidad tenant-scoped: agrega acá {@code @Filter} (ver el Javadoc de
 * {@link TenantScopedEntity}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "order_payments")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class OrderPayment extends TenantScopedEntity {

  @Column(name = "order_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String orderId;

  /** Nulo solo en un cobro "App" cargado sin caja abierta (V19). */
  @Column(name = "cash_session_id", length = 36, columnDefinition = "CHAR(36)")
  private String cashSessionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_method", nullable = false, length = 20)
  private PaymentMethod paymentMethod;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal amount;

  @Column(name = "created_by_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String createdById;
}
