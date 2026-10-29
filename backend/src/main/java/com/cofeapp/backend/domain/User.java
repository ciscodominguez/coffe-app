package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.TenantFilters;
import com.cofeapp.backend.common.tenant.TenantScopedEntity;
import com.cofeapp.backend.domain.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * Usuario del sistema (RBAC).
 *
 * <p>{@code email} es único a nivel GLOBAL (no compuesto con
 * businessId) — el login busca por email sin preguntar antes a qué
 * negocio pertenece. {@code locationId} nulo =
 * acceso a todos los locales del negocio (típicamente OWNER/ADMIN).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_users_business_id", columnList = "business_id"),
                @Index(name = "idx_users_business_id_role", columnList = "business_id, role")
        })
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class User extends TenantScopedEntity {

  /** Nulo = acceso a todos los locales del negocio. */
  @Column(name = "location_id", length = 36, columnDefinition = "CHAR(36)")
  private String locationId;

  @Column(nullable = false, unique = true, length = 255)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserRole role;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  /**
   * Versión de las sesiones (V9). Viaja en el JWT (claim "ver") y se
   * compara en cada request ({@code SessionValidator}): subirla cierra
   * todas las sesiones abiertas de este usuario al instante. Se sube al
   * cambiar la contraseña, al restablecerla y al dar de baja.
   */
  @Column(name = "token_version", nullable = false)
  private Integer tokenVersion = 0;

  /** Cierra todas las sesiones abiertas de este usuario (ver {@link #tokenVersion}). */
  public void revokeSessions() {
    this.tokenVersion = (this.tokenVersion == null ? 0 : this.tokenVersion) + 1;
  }

  @PrePersist
  private void defaults() {
    if (isActive == null) isActive = true;
    if (tokenVersion == null) tokenVersion = 0;
  }
}
