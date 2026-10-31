-- ============================================================================
-- COFFE_APP — V6__stock.sql (MySQL 8)
-- ============================================================================
-- Bloque D de las mejoras pedidas — ítems 12 y 13 ("manejo de stock").
-- Fran confirmó, respondiendo preguntas puntuales antes de esta migración:
--   - Se controla TANTO insumos/ingredientes COMO productos terminados
--     ("ambos").
--   - El stock se corrige a mano en cada apertura/cierre de local, pero se
--     descuenta AUTOMÁTICO al vender ítems simples (medialunas, sanguches).
--   - Alertas de "stock bajo" con un umbral configurable por insumo o
--     producto.
--   - Gerencia (OWNER/ADMIN/SUPERVISOR) carga insumos y ajusta stock;
--     TODOS los roles consultan cantidades (WAITER/KITCHEN incluidos).
--   - Se permite seguir vendiendo con stock negativo (no trabar una venta
--     en la hora pico) — queda marcado en rojo para corregir después.
--
-- Seis tablas nuevas:
--   - ingredients:                catálogo de insumos del negocio (ej.
--                                  "Café en grano"), compartido por todos
--                                  los locales — mismo criterio que
--                                  categories/products.
--   - ingredient_stocks:          cantidad ACTUAL de cada insumo, POR
--                                  LOCAL (el stock físico es de cada
--                                  local, mismo criterio que cash_sessions
--                                  en la V4).
--   - product_stocks:             igual que ingredient_stocks, pero para
--                                  productos en modo DIRECT (productos
--                                  terminados con su propio stock, sin
--                                  pasar por una receta).
--   - product_recipe_items:       qué insumos y cuánto consume UNA unidad
--                                  de un producto en modo RECIPE.
--   - ingredient_stock_adjustments / product_stock_adjustments:
--                                  auditoría de correcciones manuales —
--                                  mismo patrón que cash_movements de la
--                                  V4 (quién, cuándo, motivo, valor
--                                  anterior → nuevo).
--
-- Y dos columnas nuevas en `products`: `stock_mode` (NONE/DIRECT/RECIPE —
-- ver StockMode.java) y `min_stock` (umbral de alerta, solo aplica en modo
-- DIRECT). Default NONE preserva el comportamiento de todo el catálogo ya
-- cargado (sin control de stock, como hasta ahora).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Insumos (catálogo del negocio) + su stock por local
-- ----------------------------------------------------------------------------

CREATE TABLE ingredients (
  id          CHAR(36)      NOT NULL,
  business_id CHAR(36)      NOT NULL,
  name        VARCHAR(150)  NOT NULL,
  unit        VARCHAR(20)   NOT NULL,
  min_stock   DECIMAL(12,3) NULL,
  is_active   BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at  DATETIME(6)   NOT NULL,
  updated_at  DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_ingredients_business_name (business_id, name),
  KEY idx_ingredients_business_id (business_id),
  CONSTRAINT fk_ingredients_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE ingredient_stocks (
  id             CHAR(36)      NOT NULL,
  business_id    CHAR(36)      NOT NULL,
  ingredient_id  CHAR(36)      NOT NULL,
  location_id    CHAR(36)      NOT NULL,
  -- Puede quedar NEGATIVO a propósito — ver el Javadoc de IngredientStock.java.
  current_stock  DECIMAL(12,3) NOT NULL DEFAULT 0,
  created_at     DATETIME(6)   NOT NULL,
  updated_at     DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_ingredient_stocks (ingredient_id, location_id),
  KEY idx_ingredient_stocks_business_id (business_id),
  KEY idx_ingredient_stocks_location (location_id),
  CONSTRAINT fk_ingredient_stocks_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT fk_ingredient_stocks_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients (id) ON DELETE CASCADE,
  CONSTRAINT fk_ingredient_stocks_location FOREIGN KEY (location_id) REFERENCES locations (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ----------------------------------------------------------------------------
-- Productos: modo de stock + su stock DIRECT por local + receta
-- ----------------------------------------------------------------------------

ALTER TABLE products
  ADD COLUMN stock_mode VARCHAR(10)   NOT NULL DEFAULT 'NONE',
  ADD COLUMN min_stock  DECIMAL(12,3) NULL,
  ADD CONSTRAINT chk_products_stock_mode CHECK (stock_mode IN ('NONE', 'DIRECT', 'RECIPE'));

CREATE TABLE product_stocks (
  id             CHAR(36)      NOT NULL,
  business_id    CHAR(36)      NOT NULL,
  product_id     CHAR(36)      NOT NULL,
  location_id    CHAR(36)      NOT NULL,
  current_stock  DECIMAL(12,3) NOT NULL DEFAULT 0,
  created_at     DATETIME(6)   NOT NULL,
  updated_at     DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_product_stocks (product_id, location_id),
  KEY idx_product_stocks_business_id (business_id),
  KEY idx_product_stocks_location (location_id),
  CONSTRAINT fk_product_stocks_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT fk_product_stocks_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
  CONSTRAINT fk_product_stocks_location FOREIGN KEY (location_id) REFERENCES locations (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE product_recipe_items (
  id                 CHAR(36)      NOT NULL,
  business_id        CHAR(36)      NOT NULL,
  product_id         CHAR(36)      NOT NULL,
  ingredient_id      CHAR(36)      NOT NULL,
  quantity_per_unit  DECIMAL(12,3) NOT NULL,
  created_at         DATETIME(6)   NOT NULL,
  updated_at         DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_product_recipe_items (product_id, ingredient_id),
  KEY idx_product_recipe_items_business_id (business_id),
  KEY idx_product_recipe_items_product (product_id),
  CONSTRAINT fk_product_recipe_items_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT fk_product_recipe_items_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
  -- RESTRICT: un insumo usado en alguna receta no puede desaparecer sin
  -- romper esa receta (misma lógica que fk_products_category en la V1) —
  -- primero hay que sacarlo de la receta o dar de baja el insumo.
  CONSTRAINT fk_product_recipe_items_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients (id) ON DELETE RESTRICT,
  CONSTRAINT chk_product_recipe_items_qty CHECK (quantity_per_unit > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ----------------------------------------------------------------------------
-- Auditoría de ajustes manuales — mismo patrón que cash_movements (V4)
-- ----------------------------------------------------------------------------

CREATE TABLE ingredient_stock_adjustments (
  id                 CHAR(36)      NOT NULL,
  business_id        CHAR(36)      NOT NULL,
  ingredient_id      CHAR(36)      NOT NULL,
  location_id        CHAR(36)      NOT NULL,
  previous_quantity  DECIMAL(12,3) NOT NULL,
  new_quantity       DECIMAL(12,3) NOT NULL,
  delta              DECIMAL(12,3) NOT NULL,
  reason             VARCHAR(255)  NULL,
  created_by_id      CHAR(36)      NOT NULL,
  created_at         DATETIME(6)   NOT NULL,
  updated_at         DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  KEY idx_ingredient_stock_adjustments_business_id (business_id),
  KEY idx_ingredient_stock_adjustments_ingredient_location (ingredient_id, location_id),
  CONSTRAINT fk_ing_stock_adj_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT fk_ing_stock_adj_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients (id) ON DELETE CASCADE,
  CONSTRAINT fk_ing_stock_adj_location FOREIGN KEY (location_id) REFERENCES locations (id) ON DELETE CASCADE,
  CONSTRAINT fk_ing_stock_adj_created_by FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE product_stock_adjustments (
  id                 CHAR(36)      NOT NULL,
  business_id        CHAR(36)      NOT NULL,
  product_id         CHAR(36)      NOT NULL,
  location_id        CHAR(36)      NOT NULL,
  previous_quantity  DECIMAL(12,3) NOT NULL,
  new_quantity       DECIMAL(12,3) NOT NULL,
  delta              DECIMAL(12,3) NOT NULL,
  reason             VARCHAR(255)  NULL,
  created_by_id      CHAR(36)      NOT NULL,
  created_at         DATETIME(6)   NOT NULL,
  updated_at         DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  KEY idx_product_stock_adjustments_business_id (business_id),
  KEY idx_product_stock_adjustments_product_location (product_id, location_id),
  CONSTRAINT fk_prod_stock_adj_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT fk_prod_stock_adj_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
  CONSTRAINT fk_prod_stock_adj_location FOREIGN KEY (location_id) REFERENCES locations (id) ON DELETE CASCADE,
  CONSTRAINT fk_prod_stock_adj_created_by FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
