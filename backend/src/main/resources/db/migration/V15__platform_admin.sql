-- ============================================================================
-- COFFE_APP — V15__platform_admin.sql (MySQL 8)
-- ============================================================================
-- Hoja de ruta (README, sección 16, bloque 3): panel de superadmin para
-- Fran — ver todos los cafés clientes, activarlos o suspenderlos y
-- cambiarles el plan.
--
-- Decisiones de Fran:
--   - Café suspendido (o dado de baja): no puede entrar nadie. Las
--     sesiones abiertas se cortan al instante y el login avisa que está
--     suspendido. Los datos quedan; al reactivarlo vuelve todo como estaba.
--   - El plan por ahora es solo una etiqueta (no limita nada): los límites
--     y precios llegan con el cobro de la suscripción.
--
-- La cuenta de superadmin NO es un usuario de ningún café (por eso va en
-- una tabla aparte, sin business_id). Se crea sola al arrancar con
-- SUPERADMIN_EMAIL / SUPERADMIN_PASSWORD del .env (ver README).
-- ============================================================================

CREATE TABLE platform_admins (
  id             CHAR(36)     NOT NULL,
  email          VARCHAR(255) NOT NULL,
  password_hash  VARCHAR(255) NOT NULL,
  first_name     VARCHAR(100) NOT NULL,
  last_name      VARCHAR(100) NOT NULL,
  is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
  -- Igual que users.token_version (V9): si sube, se cierran sus sesiones.
  token_version  INT          NOT NULL DEFAULT 0,
  created_at     DATETIME(6)  NOT NULL,
  updated_at     DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_platform_admins_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Motivo del último cambio de estado (se le muestra al café al querer
-- entrar si está suspendido) y notas internas de Fran sobre cada cliente.
ALTER TABLE businesses
  ADD COLUMN status_reason VARCHAR(255)  NULL AFTER status,
  ADD COLUMN admin_notes   VARCHAR(2000) NULL AFTER status_reason;

-- Historial de lo que se hizo desde el panel (quién, cuándo, qué).
CREATE TABLE platform_actions (
  id           CHAR(36)     NOT NULL,
  admin_id     CHAR(36)     NOT NULL,
  business_id  CHAR(36)     NOT NULL,
  action       VARCHAR(30)  NOT NULL,
  detail       VARCHAR(500) NULL,
  created_at   DATETIME(6)  NOT NULL,
  updated_at   DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_platform_actions_business (business_id, created_at),
  CONSTRAINT fk_platform_actions_admin FOREIGN KEY (admin_id) REFERENCES platform_admins (id) ON DELETE CASCADE,
  CONSTRAINT fk_platform_actions_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
