-- ============================================================================
-- COFFE_APP — V9__accounts_security.sql (MySQL 8)
-- ============================================================================
-- Hoja de ruta (README, sección 16), paso 1.3: "Cuentas: lo básico".
--
-- 1) users.token_version — número de "versión" de las sesiones de cada
--    usuario. Viaja dentro del JWT (claim "ver") y se compara en cada
--    request: cambiar la contraseña, que te la cambie un administrador o
--    que te den de baja SUBE este número, y todas las sesiones abiertas con
--    el número viejo dejan de valer al instante (antes seguían andando hasta
--    que vencía el token, hasta 8 horas). INT, no SMALLINT: ver la nota de
--    la V8 sobre la validación de Hibernate.
--
-- 2) password_reset_tokens — links de "Olvidé mi contraseña". Se guarda
--    SOLO el hash SHA-256 del token (nunca el token en sí), con vencimiento
--    y marca de uso: un link sirve una sola vez y por tiempo limitado. No es
--    una tabla por negocio (no tiene business_id): el pedido de recuperación
--    llega sin sesión, igual que el login.
-- ============================================================================

ALTER TABLE users
  ADD COLUMN token_version INT NOT NULL DEFAULT 0 AFTER is_active;

CREATE TABLE password_reset_tokens (
  id          CHAR(36)    NOT NULL,
  user_id     CHAR(36)    NOT NULL,
  token_hash  CHAR(64)    NOT NULL,
  expires_at  DATETIME(6) NOT NULL,
  used_at     DATETIME(6) NULL,
  created_at  DATETIME(6) NOT NULL,
  updated_at  DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_password_reset_tokens_hash (token_hash),
  KEY idx_password_reset_tokens_user (user_id),
  CONSTRAINT fk_password_reset_tokens_user
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
