package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Algo que se hizo desde el panel de superadmin sobre un café (V15): suspender, cambiar el plan… */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "platform_actions")
public class PlatformAction extends BaseEntity {

  /** Nulo = lo hizo el sistema solo (V16: suspender por falta de pago, reactivar al pagar). */
  @Column(name = "admin_id", length = 36, columnDefinition = "CHAR(36)")
  private String adminId;

  @Column(name = "business_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String businessId;

  /** STATUS, PLAN, NOTES o BILLING (V16). */
  @Column(nullable = false, length = 30)
  private String action;

  @Column(length = 500)
  private String detail;
}
