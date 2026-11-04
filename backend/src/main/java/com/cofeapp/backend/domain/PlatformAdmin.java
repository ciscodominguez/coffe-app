package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuenta de superadmin de la plataforma (V15) — Fran. No pertenece a ningún
 * café (por eso no extiende {@code TenantScopedEntity}): ve y administra a
 * todos desde el panel {@code /platform}. Se crea sola al arrancar, ver
 * {@code PlatformAdminBootstrap}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "platform_admins")
public class PlatformAdmin extends BaseEntity {

  @Column(nullable = false, length = 255)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  /** Igual que {@code User#tokenVersion}: si sube, se cierran sus sesiones. */
  @Column(name = "token_version", nullable = false)
  private Integer tokenVersion = 0;

  @PrePersist
  private void defaults() {
    if (isActive == null) isActive = true;
    if (tokenVersion == null) tokenVersion = 0;
  }
}
