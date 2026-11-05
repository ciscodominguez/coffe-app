-- ============================================================================
-- COFFE_APP — V14__order_payment_items.sql (MySQL 8)
-- ============================================================================
-- Hoja de ruta (README, sección 16): dividir la cuenta entre los que están
-- en la mesa.
--
-- Decisiones de Fran:
--   - Dos formas en la ventana de cobro: en partes iguales y por lo que
--     consumió cada uno.
--   - Por productos, la app se acuerda de qué se pagó: cada cobro queda
--     atado a sus productos (y a cuántas unidades de cada uno: de "2
--     lattes" cada persona puede pagar uno), así no se cobra dos veces lo
--     mismo y cada persona puede llevarse su propio ticket.
--
-- Partes iguales no necesita nada en la base: son cobros comunes por un
-- monto calculado.
--
-- También se puede cobrar "por productos" con el QR de Mercado Pago: el
-- cobro con QR guarda qué productos paga (`mp_qr_charges.paid_items`, en
-- formato "idDelRenglón:cantidad,…") y, cuando Mercado Pago confirma, se
-- marcan como pagados igual que en un cobro común.
-- ============================================================================

CREATE TABLE order_payment_items (
  id               CHAR(36)      NOT NULL,
  business_id      CHAR(36)      NOT NULL,
  order_payment_id CHAR(36)      NOT NULL,
  order_item_id    CHAR(36)      NOT NULL,
  -- Cuántas unidades de ese renglón paga este cobro.
  quantity         INT           NOT NULL,
  -- Cuánto de este cobro corresponde a ese renglón (con descuentos ya aplicados).
  amount           DECIMAL(10,2) NOT NULL,
  created_at       DATETIME(6)   NOT NULL,
  updated_at       DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  KEY idx_order_payment_items_business_id (business_id),
  KEY idx_order_payment_items_payment (order_payment_id),
  KEY idx_order_payment_items_item (order_item_id),
  CONSTRAINT fk_opi_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT fk_opi_payment FOREIGN KEY (order_payment_id) REFERENCES order_payments (id) ON DELETE CASCADE,
  CONSTRAINT fk_opi_item FOREIGN KEY (order_item_id) REFERENCES order_items (id) ON DELETE CASCADE,
  CONSTRAINT chk_opi_quantity CHECK (quantity >= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

ALTER TABLE mp_qr_charges
  ADD COLUMN paid_items VARCHAR(4000) NULL AFTER amount;
