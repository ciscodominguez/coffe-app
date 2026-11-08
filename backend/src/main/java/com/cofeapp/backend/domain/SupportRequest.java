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
 * Un pedido de ayuda del botón de soporte (V18). Lo manda alguien de un
 * café; lo ve Fran en su panel. No es tenant-scoped a propósito: lo lista
 * el panel de superadmin (de todos los cafés); el {@code businessId} lo
 * pone el servicio a mano, del token.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "support_requests")
public class SupportRequest extends BaseEntity {

  @Column(name = "business_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String businessId;

  @Column(name = "user_id", length = 36, columnDefinition = "CHAR(36)")
  private String userId;

  @Column(name = "user_name", length = 150)
  private String userName;

  @Column(name = "user_role", length = 20)
  private String userRole;

  @Column(length = 255)
  private String page;

  @Column(length = 1000)
  private String message;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  @Column(name = "user_agent", length = 255)
  private String userAgent;

  /** PENDING o DONE. */
  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "admin_note", length = 1000)
  private String adminNote;

  @Column(name = "handled_at")
  private Instant handledAt;
}
