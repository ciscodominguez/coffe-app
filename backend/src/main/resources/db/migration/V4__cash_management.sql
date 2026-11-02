-- ============================================================================
-- COFFE_APP — V4__cash_management.sql (MySQL 8)
-- ============================================================================
-- Bloque C de las mejoras pedidas después de la Etapa 2 (ver README, sección
-- "Mejoras después de la Etapa 2") — ítems 5, 11, 14 y 15:
--   5.  Agregar método de pago.
--   11. Permitir "abono en dos etapas" — interpretado, a pedido explícito de
--       Fran, como PAGO DIVIDIDO ENTRE MÉTODOS (ej. mitad efectivo, mitad
--       tarjeta) al momento del cobro, no como seña + saldo pagados en
--       momentos distintos.
--   14. Gestión de la caja (facturación en efectivo, digital, etc.).
--   15. Vincular los cierres de caja con los lotes de Postnet.
--
-- Tres tablas nuevas:
--   - cash_sessions:   un turno de caja por local (abre con un fondo, cierra
--                       contando el efectivo físico + el lote de Postnet).
--   - cash_movements:  ingresos/egresos manuales de efectivo dentro de un
--                       turno (ej. "retiro para pagar al proveedor").
--   - order_payments:  una fila por cada línea de cobro de un pedido — así
--                       un mismo pedido puede tener varias, con métodos
--                       distintos (el pago dividido del ítem 11).
--
-- Y una columna vieja que se dropea: `orders.payment_method` existía desde
-- la V1 (Etapa 1) pero nunca se llegó a usar — ver el comentario de esa
-- columna en el Order.java de antes de esta migración. Con `order_payments`
-- soportando múltiples líneas por pedido, un único valor en `orders` ya no
-- alcanza — se reemplaza por completo, no se deja como columna muerta.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Dropea la columna sin uso de Etapa 1 — reemplazada por order_payments.
-- ----------------------------------------------------------------------------

ALTER TABLE orders
  DROP CHECK chk_orders_payment_method,
  DROP COLUMN payment_method;

-- ----------------------------------------------------------------------------
-- CAJA
-- ----------------------------------------------------------------------------

CREATE TABLE cash_sessions (
  id                      CHAR(36)      NOT NULL,
  business_id             CHAR(36)      NOT NULL,
  location_id             CHAR(36)      NOT NULL,
  status                  VARCHAR(10)   NOT NULL DEFAULT 'OPEN',
  -- Columna GENERADA: vale location_id mientras el turno sigue OPEN, y NULL
  -- en cuanto se cierra. Como MySQL no soporta índices únicos filtrados
  -- (a diferencia de Postgres) pero SÍ permite múltiples NULL en un UNIQUE
  -- KEY, este truco arma un "unique parcial": nunca puede haber dos filas
  -- OPEN para el mismo (business_id, location_id) a la vez, pero sí muchas
  -- filas CLOSED (todas con open_location_id = NULL, que no chocan entre
  -- sí). Ver el Javadoc de CashSession.java para el detalle completo.
  --
  -- IMPORTANTE: MySQL prohíbe que una FK con ON DELETE/UPDATE CASCADE o
  -- SET NULL apunte a una columna que sea "base" de una columna generada
  -- (error 1215 al crear la tabla). location_id es base de
  -- open_location_id acá abajo, así que fk_cash_sessions_location tiene
  -- que ser RESTRICT, nunca CASCADE (fk_cash_sessions_business sí puede
  -- seguir siendo CASCADE: business_id no es base de ninguna columna
  -- generada).
  open_location_id        CHAR(36)      GENERATED ALWAYS AS
                             (CASE WHEN status = 'OPEN' THEN location_id ELSE NULL END) STORED,
  opened_by_id            CHAR(36)      NOT NULL,
  opened_at               DATETIME(6)   NOT NULL,
  opening_amount          DECIMAL(10,2) NOT NULL DEFAULT 0,
  closed_by_id            CHAR(36)      NULL,
  closed_at               DATETIME(6)   NULL,
  counted_cash_amount     DECIMAL(10,2) NULL,
  postnet_batch_number    VARCHAR(50)   NULL,
  postnet_reported_amount DECIMAL(10,2) NULL,
  notes                   VARCHAR(1000) NULL,
  created_at              DATETIME(6)   NOT NULL,
  updated_at              DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_cash_sessions_open_location (business_id, open_location_id),
  KEY idx_cash_sessions_business_id (business_id),
  KEY idx_cash_sessions_business_location (business_id, location_id),
  CONSTRAINT fk_cash_sessions_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT fk_cash_sessions_location FOREIGN KEY (location_id) REFERENCES locations (id) ON DELETE RESTRICT,
  CONSTRAINT fk_cash_sessions_opened_by FOREIGN KEY (opened_by_id) REFERENCES users (id) ON DELETE RESTRICT,
  CONSTRAINT fk_cash_sessions_closed_by FOREIGN KEY (closed_by_id) REFERENCES users (id) ON DELETE RESTRICT,
  CONSTRAINT chk_cash_sessions_status CHECK (status IN ('OPEN', 'CLOSED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE cash_movements (
  id              CHAR(36)      NOT NULL,
  business_id     CHAR(36)      NOT NULL,
  cash_session_id CHAR(36)      NOT NULL,
  type            VARCHAR(10)   NOT NULL,
  amount          DECIMAL(10,2) NOT NULL,
  reason          VARCHAR(255)  NOT NULL,
  created_by_id   CHAR(36)      NOT NULL,
  created_at      DATETIME(6)   NOT NULL,
  updated_at      DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  KEY idx_cash_movements_business_id (business_id),
  KEY idx_cash_movements_session (cash_session_id),
  CONSTRAINT fk_cash_movements_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT fk_cash_movements_session FOREIGN KEY (cash_session_id) REFERENCES cash_sessions (id) ON DELETE RESTRICT,
  CONSTRAINT fk_cash_movements_created_by FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
  CONSTRAINT chk_cash_movements_type CHECK (type IN ('INCOME', 'EXPENSE')),
  CONSTRAINT chk_cash_movements_amount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ----------------------------------------------------------------------------
-- COBROS DE PEDIDOS
-- ----------------------------------------------------------------------------

CREATE TABLE order_payments (
  id              CHAR(36)      NOT NULL,
  business_id     CHAR(36)      NOT NULL,
  order_id        CHAR(36)      NOT NULL,
  cash_session_id CHAR(36)      NOT NULL,
  payment_method  VARCHAR(20)   NOT NULL,
  amount          DECIMAL(10,2) NOT NULL,
  created_by_id   CHAR(36)      NOT NULL,
  created_at      DATETIME(6)   NOT NULL,
  updated_at      DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  KEY idx_order_payments_business_id (business_id),
  KEY idx_order_payments_order (order_id),
  KEY idx_order_payments_session (cash_session_id),
  CONSTRAINT fk_order_payments_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT fk_order_payments_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
  CONSTRAINT fk_order_payments_session FOREIGN KEY (cash_session_id) REFERENCES cash_sessions (id) ON DELETE RESTRICT,
  CONSTRAINT fk_order_payments_created_by FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
  CONSTRAINT chk_order_payments_method
    CHECK (payment_method IN ('CASH', 'DEBIT_CARD', 'CREDIT_CARD', 'TRANSFER', 'QR', 'OTHER')),
  CONSTRAINT chk_order_payments_amount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
