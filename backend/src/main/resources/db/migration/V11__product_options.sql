-- ============================================================================
-- COFFE_APP — V11__product_options.sql (MySQL 8)
-- ============================================================================
-- Hoja de ruta (README, sección 16): variantes y agregados de producto
-- (tamaño chico/grande, leche de almendra, extra shot…) sin tener que
-- crear un producto por combinación.
--
-- Decisiones de Fran:
--   - Grupos REUTILIZABLES: "Leche: Entera / Descremada / Almendra +$300"
--     se crea una vez y se asigna a todos los productos que lo usan.
--   - Cada opción puede, si se quiere, descontar un insumo del stock
--     (Almendra: 150 ml de leche de almendra; Extra shot: 9 g de café).
--     Si no tiene insumo, solo suma al precio.
--
-- Cuatro tablas:
--   - option_groups:          el grupo ("Leche") y cuántas opciones se
--                             pueden/deben elegir (min_select / max_select).
--   - option_items:           cada opción del grupo, con su precio extra y
--                             el insumo que gasta (opcional).
--   - product_option_groups:  qué grupos usa cada producto.
--   - order_item_options:     lo que se eligió en cada ítem de un pedido,
--                             COPIADO al momento de la venta (nombre,
--                             precio extra, insumo y cantidad) — igual que
--                             order_items copia nombre y precio del producto.
--                             Así cambiar o borrar una opción después no
--                             altera pedidos viejos, y al cancelar un pedido
--                             se repone exactamente lo que se descontó.
-- ============================================================================

CREATE TABLE option_groups (
  id          CHAR(36)     NOT NULL,
  business_id CHAR(36)     NOT NULL,
  name        VARCHAR(100) NOT NULL,
  -- 0 = opcional; 1 = obligatorio elegir al menos una.
  min_select  INT          NOT NULL DEFAULT 0,
  -- NULL = sin límite ("elegí las que quieras").
  max_select  INT          NULL DEFAULT 1,
  -- Orden en que se preguntan al tomar el pedido (Tamaño antes que Leche…).
  position    INT          NOT NULL DEFAULT 0,
  created_at  DATETIME(6)  NOT NULL,
  updated_at  DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_option_groups_business_name (business_id, name),
  KEY idx_option_groups_business_id (business_id),
  CONSTRAINT fk_option_groups_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT chk_option_groups_min CHECK (min_select >= 0),
  CONSTRAINT chk_option_groups_max CHECK (max_select IS NULL OR (max_select >= 1 AND max_select >= min_select))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE option_items (
  id                   CHAR(36)      NOT NULL,
  business_id          CHAR(36)      NOT NULL,
  group_id             CHAR(36)      NOT NULL,
  name                 VARCHAR(100)  NOT NULL,
  -- Lo que suma al precio del producto (0 = sin cargo).
  price_delta          DECIMAL(10,2) NOT NULL DEFAULT 0,
  -- Insumo que gasta UNA unidad del producto con esta opción (opcional).
  ingredient_id        CHAR(36)      NULL,
  ingredient_quantity  DECIMAL(12,3) NULL,
  -- Viene marcada de entrada al elegir (ej. "Leche entera").
  is_default           BOOLEAN       NOT NULL DEFAULT FALSE,
  -- FALSE = "no hay" (ej. se acabó la leche de almendra): no se ofrece.
  is_available         BOOLEAN       NOT NULL DEFAULT TRUE,
  position             INT           NOT NULL DEFAULT 0,
  created_at           DATETIME(6)   NOT NULL,
  updated_at           DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  KEY idx_option_items_business_id (business_id),
  KEY idx_option_items_group (group_id),
  CONSTRAINT fk_option_items_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT fk_option_items_group FOREIGN KEY (group_id) REFERENCES option_groups (id) ON DELETE CASCADE,
  -- RESTRICT: mismo criterio que las recetas (V6) — un insumo que usa una
  -- opción no puede desaparecer sin sacarlo primero de la opción.
  CONSTRAINT fk_option_items_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients (id) ON DELETE RESTRICT,
  CONSTRAINT chk_option_items_price CHECK (price_delta >= 0),
  CONSTRAINT chk_option_items_ingredient CHECK (
    (ingredient_id IS NULL AND ingredient_quantity IS NULL)
    OR (ingredient_id IS NOT NULL AND ingredient_quantity > 0))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE product_option_groups (
  id          CHAR(36)    NOT NULL,
  business_id CHAR(36)    NOT NULL,
  product_id  CHAR(36)    NOT NULL,
  group_id    CHAR(36)    NOT NULL,
  created_at  DATETIME(6) NOT NULL,
  updated_at  DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_product_option_groups (product_id, group_id),
  KEY idx_product_option_groups_business_id (business_id),
  KEY idx_product_option_groups_group (group_id),
  CONSTRAINT fk_pog_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT fk_pog_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
  CONSTRAINT fk_pog_group FOREIGN KEY (group_id) REFERENCES option_groups (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE order_item_options (
  id                   CHAR(36)      NOT NULL,
  business_id          CHAR(36)      NOT NULL,
  order_item_id        CHAR(36)      NOT NULL,
  -- Referencias "blandas": si la opción o el grupo se borran, el pedido
  -- conserva la copia de abajo.
  option_id            CHAR(36)      NULL,
  group_name           VARCHAR(100)  NOT NULL,
  option_name          VARCHAR(100)  NOT NULL,
  price_delta          DECIMAL(10,2) NOT NULL DEFAULT 0,
  ingredient_id        CHAR(36)      NULL,
  ingredient_quantity  DECIMAL(12,3) NULL,
  -- Orden en que se muestran (el de los grupos y opciones al vender).
  position             INT           NOT NULL DEFAULT 0,
  created_at           DATETIME(6)   NOT NULL,
  updated_at           DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  KEY idx_order_item_options_business_id (business_id),
  KEY idx_order_item_options_item (order_item_id),
  CONSTRAINT fk_oio_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT fk_oio_item FOREIGN KEY (order_item_id) REFERENCES order_items (id) ON DELETE CASCADE,
  CONSTRAINT fk_oio_option FOREIGN KEY (option_id) REFERENCES option_items (id) ON DELETE SET NULL,
  CONSTRAINT fk_oio_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
