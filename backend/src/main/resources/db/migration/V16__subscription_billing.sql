-- ============================================================================
-- COFFE_APP — V16__subscription_billing.sql (MySQL 8)
-- ============================================================================
-- Hoja de ruta (README, sección 16, bloque 3): cobro de la suscripción.
--
-- Decisiones de Fran:
--   - Un solo precio mensual por café (lo tenga con 1 o varios locales). El
--     monto lo carga Fran en el panel de superadmin.
--   - 14 días de prueba gratis. La tarjeta se pide recién al suscribirse,
--     y el primer débito es el día que termina la prueba.
--   - Si no paga (no se suscribió al terminar la prueba o le rechazan la
--     tarjeta): el dueño ve un aviso, tiene 7 días para arreglarlo y si no
--     el café se suspende solo. Al pagar, se reactiva solo. Suspendido por
--     falta de pago, el DUEÑO sí puede entrar (solo a la pantalla de
--     Suscripción, para pagar); el resto del equipo no.
--
-- El cobro es con débito automático de Mercado Pago (suscripciones sin
-- plan asociado, "preapproval"), a la cuenta de Mercado Pago de Fran
-- (BILLING_MP_ACCESS_TOKEN en el .env). No tiene nada que ver con la cuenta
-- de cada café para cobrar con QR (V10).
-- ============================================================================

-- Configuración del cobro (una sola fila). Precio vacío = todavía no se
-- puede suscribir nadie (el panel lo avisa).
CREATE TABLE platform_settings (
  id              CHAR(36)      NOT NULL,
  monthly_price   DECIMAL(12,2) NULL,
  trial_days      INT           NOT NULL DEFAULT 14,
  grace_days      INT           NOT NULL DEFAULT 7,
  created_at      DATETIME(6)   NOT NULL,
  updated_at      DATETIME(6)   NOT NULL,
  PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO platform_settings (id, monthly_price, trial_days, grace_days, created_at, updated_at)
VALUES (UUID(), NULL, 14, 7, NOW(6), NOW(6));

ALTER TABLE businesses
  -- Hasta cuándo dura la prueba gratis.
  ADD COLUMN trial_ends_at         DATETIME(6)  NULL AFTER admin_notes,
  -- Hasta cuándo está pago (último débito aprobado + 1 mes).
  ADD COLUMN paid_until            DATETIME(6)  NULL AFTER trial_ends_at,
  -- "Sin cobro" (cortesía): nunca se le cobra ni se suspende solo.
  ADD COLUMN billing_exempt        BOOLEAN      NOT NULL DEFAULT FALSE AFTER paid_until,
  -- Lo suspendió el sistema por falta de pago (no Fran a mano): al pagar
  -- se reactiva solo, y el dueño puede entrar a pagar.
  ADD COLUMN billing_suspended     BOOLEAN      NOT NULL DEFAULT FALSE AFTER billing_exempt,
  -- La suscripción de Mercado Pago (preapproval) y su estado:
  -- pending (todavía no la confirmó), authorized, paused, cancelled.
  ADD COLUMN mp_preapproval_id     VARCHAR(64)  NULL AFTER billing_suspended,
  ADD COLUMN mp_preapproval_status VARCHAR(20)  NULL AFTER mp_preapproval_id,
  ADD COLUMN mp_payer_email        VARCHAR(255) NULL AFTER mp_preapproval_status,
  ADD KEY idx_businesses_preapproval (mp_preapproval_id);

-- Cafés que ya existían: 14 días de prueba desde hoy (para que nadie quede
-- vencido de golpe al actualizar). Los que Fran ya había pasado a "Activo"
-- a mano quedan "Sin cobro" (se puede cambiar desde el panel).
UPDATE businesses SET trial_ends_at = DATE_ADD(NOW(6), INTERVAL 14 DAY);
UPDATE businesses SET billing_exempt = TRUE WHERE status = 'ACTIVE';

-- Cada débito que hizo Mercado Pago (aprobado o rechazado).
CREATE TABLE subscription_payments (
  id                         CHAR(36)      NOT NULL,
  business_id                CHAR(36)      NOT NULL,
  mp_authorized_payment_id   VARCHAR(64)   NOT NULL,
  mp_payment_id              VARCHAR(64)   NULL,
  amount                     DECIMAL(12,2) NOT NULL,
  -- approved / rejected (lo que dijo Mercado Pago del débito).
  status                     VARCHAR(20)   NOT NULL,
  status_detail              VARCHAR(100)  NULL,
  charged_at                 DATETIME(6)   NOT NULL,
  -- Con este pago, el café quedó pago hasta acá (solo los aprobados).
  covers_until               DATETIME(6)   NULL,
  created_at                 DATETIME(6)   NOT NULL,
  updated_at                 DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_subscription_payments_mp (mp_authorized_payment_id),
  KEY idx_subscription_payments_business (business_id, charged_at),
  CONSTRAINT fk_subscription_payments_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- El historial del panel ahora también anota lo que hace el sistema solo
-- (suspender por falta de pago, reactivar al pagar): sin admin.
ALTER TABLE platform_actions MODIFY admin_id CHAR(36) NULL;
