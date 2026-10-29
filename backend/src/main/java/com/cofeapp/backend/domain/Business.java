package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.BaseEntity;
import com.cofeapp.backend.domain.enums.BusinessPlan;
import com.cofeapp.backend.domain.enums.BusinessStatus;
import com.cofeapp.backend.domain.enums.FiscalCondition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * El tenant. Cada fila es un cliente (cafetería) del SaaS.
 *
 * <p>A propósito NO extiende {@link com.cofeapp.backend.common.tenant.TenantScopedEntity}:
 * es el tenant en sí, no un registro DENTRO de un tenant — no tiene (ni
 * necesita) {@code businessId} ni {@code @Filter}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "businesses")
public class Business extends BaseEntity {

  @Column(name = "legal_name", nullable = false, length = 150)
  private String legalName;

  @Column(name = "trade_name", length = 150)
  private String tradeName;

  @Column(name = "tax_id", nullable = false, unique = true, length = 20)
  private String taxId;

  @Enumerated(EnumType.STRING)
  @Column(name = "fiscal_condition", nullable = false, length = 30)
  private FiscalCondition fiscalCondition;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private BusinessPlan plan = BusinessPlan.TRIAL;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private BusinessStatus status = BusinessStatus.TRIAL;

  /** V15 — por qué se suspendió o dio de baja (se le muestra al café al querer entrar). */
  @Column(name = "status_reason", length = 255)
  private String statusReason;

  /** V15 — notas internas del superadmin sobre este cliente (nunca las ve el café). */
  @Column(name = "admin_notes", length = 2000)
  private String adminNotes;

  /** V16 — hasta cuándo dura la prueba gratis. */
  @Column(name = "trial_ends_at")
  private Instant trialEndsAt;

  /** V16 — hasta cuándo está pago (último débito aprobado + 1 mes). */
  @Column(name = "paid_until")
  private Instant paidUntil;

  /** V16 — "Sin cobro" (cortesía de Fran): nunca se cobra ni se suspende solo. */
  @Column(name = "billing_exempt", nullable = false)
  private Boolean billingExempt = false;

  /** V16 — lo suspendió el sistema por falta de pago: al pagar se reactiva solo, y el dueño puede entrar a pagar. */
  @Column(name = "billing_suspended", nullable = false)
  private Boolean billingSuspended = false;

  /** V16 — la suscripción de Mercado Pago (preapproval). */
  @Column(name = "mp_preapproval_id", length = 64)
  private String mpPreapprovalId;

  /** V16 — pending, authorized, paused o cancelled (lo que dice Mercado Pago). */
  @Column(name = "mp_preapproval_status", length = 20)
  private String mpPreapprovalStatus;

  /** V16 — email de la cuenta de Mercado Pago con la que se suscribió. */
  @Column(name = "mp_payer_email", length = 255)
  private String mpPayerEmail;

  /** V17 — última versión de los términos y la política de privacidad que aceptó el dueño. */
  @Column(name = "terms_version", length = 20)
  private String termsVersion;

  @Column(name = "terms_accepted_at")
  private Instant termsAcceptedAt;

  /** Suspendido o dado de baja: nadie del café puede entrar. */
  public boolean isBlocked() {
    return status == BusinessStatus.SUSPENDED || status == BusinessStatus.CANCELLED;
  }

  @PrePersist
  private void defaults() {
    if (plan == null) plan = BusinessPlan.TRIAL;
    if (status == null) status = BusinessStatus.TRIAL;
    if (billingExempt == null) billingExempt = false;
    if (billingSuspended == null) billingSuspended = false;
    if (trialEndsAt == null) trialEndsAt = Instant.now().plus(java.time.Duration.ofDays(14));
  }

  /** V16 — suspendido por el sistema por falta de pago (no a mano por Fran). */
  public boolean isBlockedForPayment() {
    return status == BusinessStatus.SUSPENDED && Boolean.TRUE.equals(billingSuspended);
  }
}
