-- ============================================================================
-- COFFE_APP — V12__order_discounts.sql (MySQL 8)
-- ============================================================================
-- Hoja de ruta (README, sección 16): descuentos sobre un ítem o sobre el
-- pedido.
--
-- Decisiones de Fran:
--   - Lista fija + libre: el dueño arma una lista ("Jubilados 10%",
--     "Empleado 20%", "Cortesía $500") que se elige con un toque, y además
--     se puede poner uno a mano en el momento (porcentaje o monto) con un
--     motivo.
--   - El mozo puede usar los de la lista, salvo los marcados "pide
--     autorización". Los descuentos a mano y los que piden autorización,
--     solo supervisor, administrador o dueño.
--
-- La lista usa la tabla `discounts`, que existe desde la V1 sin uso. Lo
-- aplicado a cada pedido va en `order_discounts` (uno por ítem y uno para
-- todo el pedido, como máximo), con quién lo aplicó y por qué. Los montos
-- resultantes siguen en las columnas que ya existían:
--   - order_items.discount_amount / discount_id / subtotal (neto del ítem)
--   - orders.discount_total (descuento sobre todo el pedido) y total.
-- ============================================================================

ALTER TABLE discounts
  ADD CONSTRAINT chk_discounts_value CHECK (value > 0 AND (type <> 'PERCENTAGE' OR value <= 100));

CREATE TABLE order_discounts (
  id               CHAR(36)      NOT NULL,
  business_id      CHAR(36)      NOT NULL,
  order_id         CHAR(36)      NOT NULL,
  -- NULL = descuento sobre todo el pedido.
  order_item_id    CHAR(36)      NULL,
  -- NULL = descuento puesto a mano (no viene de la lista).
  discount_id      CHAR(36)      NULL,
  -- Copia al aplicarlo: cambiar la lista después no cambia pedidos viejos.
  name             VARCHAR(150)  NOT NULL,
  type             VARCHAR(20)   NOT NULL,
  value            DECIMAL(10,2) NOT NULL,
  -- Lo que descuenta de verdad (se recalcula si cambia el pedido).
  amount           DECIMAL(10,2) NOT NULL DEFAULT 0,
  -- TRUE = a mano o "pide autorización": solo supervisor/admin/dueño lo aplica o lo quita.
  supervisor_only  BOOLEAN       NOT NULL DEFAULT FALSE,
  reason           VARCHAR(255)  NULL,
  created_by_id    CHAR(36)      NOT NULL,
  created_at       DATETIME(6)   NOT NULL,
  updated_at       DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  KEY idx_order_discounts_business_id (business_id),
  KEY idx_order_discounts_order (order_id),
  CONSTRAINT fk_order_discounts_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT fk_order_discounts_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
  CONSTRAINT fk_order_discounts_item FOREIGN KEY (order_item_id) REFERENCES order_items (id) ON DELETE CASCADE,
  CONSTRAINT fk_order_discounts_discount FOREIGN KEY (discount_id) REFERENCES discounts (id) ON DELETE SET NULL,
  CONSTRAINT fk_order_discounts_created_by FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
  CONSTRAINT chk_order_discounts_type CHECK (type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
  CONSTRAINT chk_order_discounts_value CHECK (value > 0 AND (type <> 'PERCENTAGE' OR value <= 100))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
