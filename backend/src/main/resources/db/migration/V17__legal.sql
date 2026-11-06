-- ============================================================================
-- COFFE_APP — V17__legal.sql (MySQL 8)
-- ============================================================================
-- Hoja de ruta (README, sección 16, bloque 3): los papeles — términos y
-- condiciones, política de privacidad, y los botones de arrepentimiento y
-- de baja (Defensa del Consumidor).
--
-- Decisiones de Fran:
--   - Casilla obligatoria al registrarse. Se guarda qué versión se aceptó,
--     cuándo, quién y desde dónde. Si se publica una versión nueva, al
--     dueño le aparece para aceptarla al entrar.
--   - "Botón de arrepentimiento" y "Botón de baja": formulario público (sin
--     tener que entrar) que da un código de trámite al instante y le llega
--     a Fran a su panel.
-- ============================================================================

-- Cada aceptación de los términos y la política de privacidad (historial).
CREATE TABLE legal_acceptances (
  id           CHAR(36)     NOT NULL,
  business_id  CHAR(36)     NOT NULL,
  user_id      CHAR(36)     NULL,
  -- Versión de los documentos que se aceptó (ej. "2026-10-01").
  version      VARCHAR(20)  NOT NULL,
  accepted_at  DATETIME(6)  NOT NULL,
  ip           VARCHAR(64)  NULL,
  user_agent   VARCHAR(255) NULL,
  created_at   DATETIME(6)  NOT NULL,
  updated_at   DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_legal_acceptances_business (business_id, accepted_at),
  CONSTRAINT fk_legal_acceptances_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- La última versión que aceptó cada café (los que ya existían quedan en
-- NULL: el dueño la acepta la próxima vez que entra).
ALTER TABLE businesses
  ADD COLUMN terms_version     VARCHAR(20) NULL AFTER mp_payer_email,
  ADD COLUMN terms_accepted_at DATETIME(6) NULL AFTER terms_version;

-- Pedidos del "Botón de arrepentimiento" y del "Botón de baja".
CREATE TABLE service_requests (
  id           CHAR(36)      NOT NULL,
  -- Código de trámite que se le da al instante (ej. "BAJ-7K3F9Q").
  code         VARCHAR(20)   NOT NULL,
  -- WITHDRAWAL (arrepentimiento) o CANCELLATION (baja).
  type         VARCHAR(20)   NOT NULL,
  -- El café, si el CUIT y el email coinciden con los de su dueño.
  business_id  CHAR(36)      NULL,
  contact_name VARCHAR(150)  NOT NULL,
  email        VARCHAR(255)  NOT NULL,
  tax_id       VARCHAR(20)   NULL,
  cafe_name    VARCHAR(150)  NULL,
  message      VARCHAR(2000) NULL,
  -- PENDING (sin atender) o DONE.
  status       VARCHAR(20)   NOT NULL,
  -- Qué se hizo solo al recibirlo (ej. "Débito automático cancelado").
  result       VARCHAR(255)  NULL,
  admin_note   VARCHAR(1000) NULL,
  handled_at   DATETIME(6)   NULL,
  ip           VARCHAR(64)   NULL,
  created_at   DATETIME(6)   NOT NULL,
  updated_at   DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_service_requests_code (code),
  KEY idx_service_requests_status (status, created_at),
  CONSTRAINT fk_service_requests_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
