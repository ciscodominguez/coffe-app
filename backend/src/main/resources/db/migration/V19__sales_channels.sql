-- ============================================================================
-- COFFE_APP — V19__sales_channels.sql (MySQL 8)
-- ============================================================================
-- Apps de delivery, Fase A (ver docs/INTEGRACION-APPS-DELIVERY.md):
--
-- 1. CANALES DE VENTA por local: qué apps usa cada local (PedidosYa, Rappi,
--    Uber Eats u "otra app" con nombre libre), en qué modo y qué comisión
--    cobra cada una. Todo arranca apagado: un café sin apps no tiene filas
--    acá y no ve nada nuevo.
--    Modos: OFF (apagado), MANUAL (el pedido se carga a mano en segundos),
--    AUTO (el pedido entra solo; para cuando COFFE_APP esté aprobado como
--    integrador — todavía no se puede elegir).
--
-- 2. PEDIDOS DE APP: un pedido normal con datos extra (de qué app vino, su
--    número en la app, cliente, hora de retiro, si ya lo cobró la app y la
--    comisión de ese momento). El nombre y la comisión se copian al pedido:
--    si después cambian en el canal, los pedidos viejos no cambian.
--    No se puede cargar dos veces el mismo número de pedido de una app.
--
-- 3. COBRO "APP": lo que cobra la app no entra en la caja del mostrador.
--    Se registra con el método APP ("a cobrar de la app"). Si hay una caja
--    abierta se asocia a ella (aparece en el cierre, aparte del efectivo);
--    si no, queda sin caja.
-- ============================================================================

CREATE TABLE sales_channels (
  id              CHAR(36)     NOT NULL,
  business_id     CHAR(36)     NOT NULL,
  location_id     CHAR(36)     NOT NULL,
  kind            VARCHAR(20)  NOT NULL,
  name            VARCHAR(40)  NOT NULL,
  mode            VARCHAR(10)  NOT NULL DEFAULT 'OFF',
  commission_pct  DECIMAL(5,2) NOT NULL DEFAULT 0,
  created_at      DATETIME(6)  NOT NULL,
  updated_at      DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_sales_channels_business_id (business_id),
  UNIQUE KEY uq_sales_channels_location_name (location_id, name),
  CONSTRAINT fk_sales_channels_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT fk_sales_channels_location FOREIGN KEY (location_id) REFERENCES locations (id) ON DELETE CASCADE,
  CONSTRAINT chk_sales_channels_kind CHECK (kind IN ('PEDIDOSYA', 'RAPPI', 'UBER_EATS', 'OTHER')),
  CONSTRAINT chk_sales_channels_mode CHECK (mode IN ('OFF', 'MANUAL', 'AUTO')),
  CONSTRAINT chk_sales_channels_commission CHECK (commission_pct >= 0 AND commission_pct <= 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

ALTER TABLE orders
  ADD COLUMN channel_id             CHAR(36)     NULL,
  ADD COLUMN channel_kind           VARCHAR(20)  NULL,
  ADD COLUMN channel_name           VARCHAR(40)  NULL,
  ADD COLUMN channel_order_ref      VARCHAR(40)  NULL,
  ADD COLUMN channel_customer       VARCHAR(80)  NULL,
  ADD COLUMN channel_pickup_at      DATETIME(6)  NULL,
  ADD COLUMN channel_paid_by_app    BOOLEAN      NULL,
  ADD COLUMN channel_commission_pct DECIMAL(5,2) NULL,
  ADD UNIQUE KEY uq_orders_channel_ref (channel_id, channel_order_ref),
  ADD CONSTRAINT fk_orders_channel FOREIGN KEY (channel_id) REFERENCES sales_channels (id) ON DELETE RESTRICT;

ALTER TABLE order_payments
  MODIFY cash_session_id CHAR(36) NULL,
  DROP CHECK chk_order_payments_method,
  ADD CONSTRAINT chk_order_payments_method
    CHECK (payment_method IN ('CASH', 'DEBIT_CARD', 'CREDIT_CARD', 'TRANSFER', 'QR', 'OTHER', 'APP'));
