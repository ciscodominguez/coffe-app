package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.TenantFilters;
import com.cofeapp.backend.common.tenant.TenantScopedEntity;
import com.cofeapp.backend.domain.enums.QrChargeStatus;
import com.cofeapp.backend.domain.enums.QrMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * Un intento de cobro con QR de Mercado Pago sobre un pedido (V10).
 *
 * <p>Ciclo de vida: nace {@link QrChargeStatus#PENDING} con la orden ya
 * creada en Mercado Pago ({@code mpOrderId}; su {@code external_reference}
 * es el id de esta fila). Se entera del resultado de tres formas, todas
 * por el mismo camino ({@code QrChargeService#sync}): la pantalla de cobro
 * pregunta cada pocos segundos, un proceso de fondo revisa los pendientes,
 * y (con la app publicada) Mercado Pago avisa por webhook. Cuando MP dice
 * "processed", se registra el cobro normal del pedido ({@link
 * OrderPayment}, método QR) en la caja abierta — una sola vez, aunque
 * lleguen los tres avisos juntos (se bloquea la fila mientras tanto).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "mp_qr_charges")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class MpQrCharge extends TenantScopedEntity {

  @Column(name = "location_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String locationId;

  @Column(name = "order_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String orderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private QrMode mode;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal amount;

  /**
   * V14 — si es un cobro "por productos": qué renglones y cuántas unidades
   * paga, como "idDelRenglón:cantidad,…". Nulo = cobro de un monto común.
   */
  @Column(name = "paid_items", length = 4000)
  private String paidItems;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private QrChargeStatus status = QrChargeStatus.PENDING;

  @Column(name = "mp_order_id", length = 64)
  private String mpOrderId;

  /** Contenido del QR dinámico (lo dibuja el frontend). Nulo en modo STATIC. */
  @Column(name = "qr_data", length = 1000)
  private String qrData;

  @Column(name = "mp_status", length = 30)
  private String mpStatus;

  @Column(name = "mp_status_detail", length = 60)
  private String mpStatusDetail;

  @Column(name = "order_payment_id", length = 36, columnDefinition = "CHAR(36)")
  private String orderPaymentId;

  @Column(name = "status_message", length = 255)
  private String statusMessage;

  @Column(name = "created_by_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String createdById;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "resolved_at")
  private Instant resolvedAt;
}
