package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un pedido del "Botón de arrepentimiento" o del "Botón de baja" (V17).
 * Llega sin sesión (formulario público); Fran lo ve en su panel.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "service_requests")
public class ServiceRequest extends BaseEntity {

  @Column(nullable = false, length = 20)
  private String code;

  /** WITHDRAWAL (arrepentimiento) o CANCELLATION (baja). */
  @Column(nullable = false, length = 20)
  private String type;

  @Column(name = "business_id", length = 36, columnDefinition = "CHAR(36)")
  private String businessId;

  @Column(name = "contact_name", nullable = false, length = 150)
  private String contactName;

  @Column(nullable = false, length = 255)
  private String email;

  @Column(name = "tax_id", length = 20)
  private String taxId;

  @Column(name = "cafe_name", length = 150)
  private String cafeName;

  @Column(length = 2000)
  private String message;

  /** PENDING o DONE. */
  @Column(nullable = false, length = 20)
  private String status;

  @Column(length = 255)
  private String result;

  @Column(name = "admin_note", length = 1000)
  private String adminNote;

  @Column(name = "handled_at")
  private Instant handledAt;

  @Column(length = 64)
  private String ip;
}
