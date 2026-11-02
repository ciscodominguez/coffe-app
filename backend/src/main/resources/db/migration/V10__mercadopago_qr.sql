-- ============================================================================
-- COFFE_APP — V10__mercadopago_qr.sql (MySQL 8)
-- ============================================================================
-- Hoja de ruta (README, sección 16), paso 2.2: "Pagos con QR de Mercado Pago
-- integrados".
--
-- 1) mp_accounts — la cuenta de Mercado Pago de cada negocio (una por
--    negocio). El dueño pega su Access Token en "Cobros con QR"; se guarda
--    CIFRADO (AES-GCM, clave en MP_TOKEN_ENCRYPTION_KEY), nunca en claro.
--
-- 2) locations.mp_* — cada local es una "caja" (POS) de Mercado Pago:
--    - mp_qr_mode: DYNAMIC = QR nuevo en pantalla por cada cobro;
--      STATIC = QR impreso fijo en el mostrador, al que se le carga el monto.
--    - mp_store_id / mp_pos_id / mp_pos_external_id: la sucursal y la caja
--      que el sistema crea en la cuenta de MP la primera vez.
--    - mp_qr_image_url: la imagen del QR fijo de esa caja (para imprimirlo).
--
-- 3) mp_qr_charges — cada intento de cobro con QR. Queda PENDING hasta que
--    Mercado Pago confirma (PAID, y ahí se registra el cobro normal en
--    order_payments, método QR, en la caja abierta), vence (EXPIRED), se
--    cancela (CANCELLED) o falla (FAILED). PAID_UNAPPLIED = MP cobró pero no
--    se pudo registrar solo (ej. el pedido ya estaba cerrado): se muestra
--    para registrarlo a mano.
-- ============================================================================

CREATE TABLE mp_accounts (
  id                     CHAR(36)      NOT NULL,
  business_id            CHAR(36)      NOT NULL,
  access_token_encrypted VARCHAR(1024) NOT NULL,
  mp_user_id             VARCHAR(30)   NOT NULL,
  mp_nickname            VARCHAR(120)  NULL,
  test_mode              BOOLEAN       NOT NULL DEFAULT FALSE,
  connected_by_id        CHAR(36)      NOT NULL,
  created_at             DATETIME(6)   NOT NULL,
  updated_at             DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_mp_accounts_business (business_id),
  CONSTRAINT fk_mp_accounts_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT fk_mp_accounts_connected_by FOREIGN KEY (connected_by_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

ALTER TABLE locations
  ADD COLUMN mp_qr_mode         VARCHAR(10)  NOT NULL DEFAULT 'DYNAMIC' AFTER receipt_paper_width_mm,
  ADD COLUMN mp_store_id        VARCHAR(30)  NULL AFTER mp_qr_mode,
  ADD COLUMN mp_pos_id          VARCHAR(30)  NULL AFTER mp_store_id,
  ADD COLUMN mp_pos_external_id VARCHAR(40)  NULL AFTER mp_pos_id,
  ADD COLUMN mp_qr_image_url    VARCHAR(500) NULL AFTER mp_pos_external_id,
  ADD CONSTRAINT chk_locations_mp_qr_mode CHECK (mp_qr_mode IN ('DYNAMIC', 'STATIC'));

CREATE TABLE mp_qr_charges (
  id               CHAR(36)      NOT NULL,
  business_id      CHAR(36)      NOT NULL,
  location_id      CHAR(36)      NOT NULL,
  order_id         CHAR(36)      NOT NULL,
  mode             VARCHAR(10)   NOT NULL,
  amount           DECIMAL(10,2) NOT NULL,
  status           VARCHAR(20)   NOT NULL,
  mp_order_id      VARCHAR(64)   NULL,
  qr_data          VARCHAR(1000) NULL,
  mp_status        VARCHAR(30)   NULL,
  mp_status_detail VARCHAR(60)   NULL,
  order_payment_id CHAR(36)      NULL,
  status_message   VARCHAR(255)  NULL,
  created_by_id    CHAR(36)      NOT NULL,
  expires_at       DATETIME(6)   NOT NULL,
  resolved_at      DATETIME(6)   NULL,
  created_at       DATETIME(6)   NOT NULL,
  updated_at       DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_mp_qr_charges_mp_order (mp_order_id),
  KEY idx_mp_qr_charges_business_id (business_id),
  KEY idx_mp_qr_charges_order_status (order_id, status),
  KEY idx_mp_qr_charges_location_status (location_id, status),
  KEY idx_mp_qr_charges_status (status),
  CONSTRAINT fk_mp_qr_charges_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT fk_mp_qr_charges_location FOREIGN KEY (location_id) REFERENCES locations (id) ON DELETE CASCADE,
  CONSTRAINT fk_mp_qr_charges_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
  CONSTRAINT fk_mp_qr_charges_payment FOREIGN KEY (order_payment_id) REFERENCES order_payments (id) ON DELETE SET NULL,
  CONSTRAINT fk_mp_qr_charges_created_by FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
  CONSTRAINT chk_mp_qr_charges_mode CHECK (mode IN ('DYNAMIC', 'STATIC')),
  CONSTRAINT chk_mp_qr_charges_status
    CHECK (status IN ('PENDING', 'PAID', 'PAID_UNAPPLIED', 'CANCELLED', 'EXPIRED', 'FAILED')),
  CONSTRAINT chk_mp_qr_charges_amount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
