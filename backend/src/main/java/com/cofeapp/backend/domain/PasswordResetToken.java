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
 * Link de "Olvidé mi contraseña" (V9). NO es tenant-scoped a propósito
 * (extiende {@link BaseEntity}, no {@code TenantScopedEntity}): el pedido de
 * recuperación llega sin sesión, igual que el login, y se busca por el hash
 * del token a nivel global.
 *
 * <p>Nunca se guarda el token en claro: solo su SHA-256 ({@code tokenHash}).
 * Quien tenga acceso a la base no puede usar los links pendientes.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends BaseEntity {

  @Column(name = "user_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String userId;

  @Column(name = "token_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;
}
