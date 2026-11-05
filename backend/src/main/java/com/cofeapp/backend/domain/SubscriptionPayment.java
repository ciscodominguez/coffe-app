package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un débito de la suscripción que hizo Mercado Pago (V16), aprobado o
 * rechazado. No es tenant-scoped: lo leen el sistema, el panel de Fran y la
 * pantalla de Suscripción del propio café (filtrando por su id a mano).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "subscription_payments")
public class SubscriptionPayment extends BaseEntity {

  @Column(name = "business_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String businessId;

  @Column(name = "mp_authorized_payment_id", nullable = false, length = 64)
  private String mpAuthorizedPaymentId;

  @Column(name = "mp_payment_id", length = 64)
  private String mpPaymentId;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  /** approved o rejected. */
  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "status_detail", length = 100)
  private String statusDetail;

  @Column(name = "charged_at", nullable = false)
  private Instant chargedAt;

  /** Hasta cuándo quedó pago el café con este débito (solo los aprobados). */
  @Column(name = "covers_until")
  private Instant coversUntil;
}
