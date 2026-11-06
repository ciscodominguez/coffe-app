package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.TenantFilters;
import com.cofeapp.backend.common.tenant.TenantScopedEntity;
import com.cofeapp.backend.domain.enums.SalesChannelKind;
import com.cofeapp.backend.domain.enums.SalesChannelMode;
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
 * Una app de delivery que usa un local (V19): PedidosYa, Rappi, Uber Eats
 * u "otra app". Un café sin apps no tiene ninguna fila: todo arranca
 * apagado. Ver docs/INTEGRACION-APPS-DELIVERY.md.
 *
 * <p>Entidad tenant-scoped.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sales_channels")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class SalesChannel extends TenantScopedEntity {

  @Column(name = "location_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String locationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SalesChannelKind kind;

  /** Lo que se ve en la comanda y los reportes ("PedidosYa", o el nombre que el café le puso a otra app). */
  @Column(nullable = false, length = 40)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private SalesChannelMode mode = SalesChannelMode.OFF;

  /** Comisión que cobra la app, en % (solo para los reportes). */
  @Column(name = "commission_pct", nullable = false, precision = 5, scale = 2)
  private BigDecimal commissionPct = BigDecimal.ZERO;

  public boolean isOn() {
    return mode != null && mode != SalesChannelMode.OFF;
  }
}
