package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuración del cobro de la suscripción (V16). Una sola fila; la edita
 * Fran desde el panel de superadmin.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "platform_settings")
public class PlatformSettings extends BaseEntity {

  /** Abono mensual por café, en pesos. Nulo = todavía no se puede suscribir nadie. */
  @Column(name = "monthly_price", precision = 12, scale = 2)
  private BigDecimal monthlyPrice;

  /** Días de prueba gratis de un café nuevo. */
  @Column(name = "trial_days", nullable = false)
  private Integer trialDays = 14;

  /** Días que tiene un café para pagar antes de suspenderse solo. */
  @Column(name = "grace_days", nullable = false)
  private Integer graceDays = 7;

  /** V18 — WhatsApp de soporte (formato internacional, solo números: 5492235550000). */
  @Column(name = "support_whatsapp", length = 30)
  private String supportWhatsapp;
}
