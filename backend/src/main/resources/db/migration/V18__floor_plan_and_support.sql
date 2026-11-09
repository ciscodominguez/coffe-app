-- ============================================================================
-- COFFE_APP — V18__floor_plan_and_support.sql (MySQL 8)
-- ============================================================================
-- Dos pedidos de Fran:
--
-- 1. PLANO DEL SALÓN: ver las mesas como están en el café. Por local, una
--    o más zonas (Salón, Vereda, Planta alta…). Cada mesa tiene su lugar,
--    forma y tamaño dentro de su zona, y se pueden marcar referencias (la
--    barra, la puerta, una pared…). El mozo ve el plano con cada mesa libre
--    u ocupada, hace cuánto y cuánto va.
--
--    Coordenadas: cada zona es un lienzo de 100 x 75 unidades (proporción
--    4:3, apaisado). x/y es la esquina de arriba a la izquierda; ancho y
--    alto en las mismas unidades. Una mesa sin zona o sin posición está
--    "sin ubicar" (se sigue viendo en la lista de siempre).
--
-- 2. BOTÓN DE SOPORTE: cualquiera del café, si se traba el sistema, puede
--    escribirle a Fran por WhatsApp (con un mensaje ya armado) y además le
--    queda el pedido de ayuda en el panel de superadmin con los datos
--    técnicos.
-- ============================================================================

CREATE TABLE floor_areas (
  id           CHAR(36)    NOT NULL,
  business_id  CHAR(36)    NOT NULL,
  location_id  CHAR(36)    NOT NULL,
  name         VARCHAR(50) NOT NULL,
  position     INT         NOT NULL DEFAULT 0,
  created_at   DATETIME(6) NOT NULL,
  updated_at   DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_floor_areas_location_name (location_id, name),
  KEY idx_floor_areas_business_id (business_id),
  CONSTRAINT fk_floor_areas_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT fk_floor_areas_location FOREIGN KEY (location_id) REFERENCES locations (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

ALTER TABLE tables
  ADD COLUMN area_id CHAR(36)     NULL AFTER location_id,
  ADD COLUMN pos_x   DECIMAL(6,2) NULL AFTER is_active,
  ADD COLUMN pos_y   DECIMAL(6,2) NULL AFTER pos_x,
  ADD COLUMN width   DECIMAL(6,2) NULL AFTER pos_y,
  ADD COLUMN height  DECIMAL(6,2) NULL AFTER width,
  ADD COLUMN shape   VARCHAR(10)  NOT NULL DEFAULT 'SQUARE' AFTER height,
  ADD CONSTRAINT fk_tables_area FOREIGN KEY (area_id) REFERENCES floor_areas (id) ON DELETE SET NULL,
  ADD CONSTRAINT chk_tables_shape CHECK (shape IN ('ROUND', 'SQUARE', 'RECT'));

-- Referencias dibujadas en el plano (no son mesas).
CREATE TABLE floor_items (
  id           CHAR(36)     NOT NULL,
  business_id  CHAR(36)     NOT NULL,
  area_id      CHAR(36)     NOT NULL,
  kind         VARCHAR(20)  NOT NULL,
  label        VARCHAR(40)  NULL,
  pos_x        DECIMAL(6,2) NOT NULL,
  pos_y        DECIMAL(6,2) NOT NULL,
  width        DECIMAL(6,2) NOT NULL,
  height       DECIMAL(6,2) NOT NULL,
  created_at   DATETIME(6)  NOT NULL,
  updated_at   DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_floor_items_business_id (business_id),
  CONSTRAINT fk_floor_items_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT fk_floor_items_area FOREIGN KEY (area_id) REFERENCES floor_areas (id) ON DELETE CASCADE,
  CONSTRAINT chk_floor_items_kind CHECK (kind IN ('BAR', 'DOOR', 'WALL', 'WINDOW', 'RESTROOM', 'KITCHEN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Número de WhatsApp de soporte (lo carga Fran en el panel). Formato
-- internacional, solo números (ej. 5492235550000).
ALTER TABLE platform_settings ADD COLUMN support_whatsapp VARCHAR(30) NULL AFTER grace_days;

-- Pedidos de ayuda del botón de soporte.
CREATE TABLE support_requests (
  id           CHAR(36)      NOT NULL,
  business_id  CHAR(36)      NOT NULL,
  user_id      CHAR(36)      NULL,
  user_name    VARCHAR(150)  NULL,
  user_role    VARCHAR(20)   NULL,
  -- En qué pantalla estaba (ej. "/pedidos").
  page         VARCHAR(255)  NULL,
  message      VARCHAR(1000) NULL,
  -- El último error que vio la app (pantalla o servidor), si hubo.
  last_error   VARCHAR(1000) NULL,
  user_agent   VARCHAR(255)  NULL,
  -- PENDING (sin atender) o DONE.
  status       VARCHAR(20)   NOT NULL,
  admin_note   VARCHAR(1000) NULL,
  handled_at   DATETIME(6)   NULL,
  created_at   DATETIME(6)   NOT NULL,
  updated_at   DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  KEY idx_support_requests_status (status, created_at),
  CONSTRAINT fk_support_requests_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
