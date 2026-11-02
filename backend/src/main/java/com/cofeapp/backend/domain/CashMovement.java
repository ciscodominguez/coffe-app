package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.TenantFilters;
import com.cofeapp.backend.common.tenant.TenantScopedEntity;
import com.cofeapp.backend.domain.enums.CashMovementType;
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
 * Un movimiento manual de efectivo dentro de un turno de caja — ítem 14 de
 * las mejoras pedidas (ej. "retiro para pagar al proveedor", "se agregó
 * cambio al cajón"). Siempre en efectivo y siempre con un {@code reason}
 * obligatorio (a diferencia de {@link OrderPayment}, que es el cobro
 * automático de un pedido) — ver el Javadoc de {@code CashMovementType}
 * sobre {@code INCOME}/{@code EXPENSE}.
 *
 * <p>Sin endpoint de edición/baja a propósito: es un registro contable,
 * igual que {@link com.cofeapp.backend.domain.OrderItem} — una vez
 * cargado, no se toca (si se cargó mal, se carga un movimiento inverso
 * nuevo, nunca se edita el original).
 *
 * <p>Entidad tenant-scoped: agrega acá {@code @Filter} (ver el Javadoc de
 * {@link TenantScopedEntity}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cash_movements")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class CashMovement extends TenantScopedEntity {

  @Column(name = "cash_session_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String cashSessionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private CashMovementType type;

  /** Siempre positivo — {@link #type} decide el signo al calcular el efectivo esperado del turno. */
  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 255)
  private String reason;

  @Column(name = "created_by_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String createdById;
}
