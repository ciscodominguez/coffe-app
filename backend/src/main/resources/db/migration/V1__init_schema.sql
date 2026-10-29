-- ============================================================================
-- COFE_APP — V1__init_schema.sql (MySQL 8)
-- ============================================================================
-- Traducción 1:1 de apps/backend/prisma/schema.prisma (PostgreSQL) a MySQL,
-- manteniendo los mismos principios de diseño que documentaba ese archivo:
--
--  1. Toda tabla de negocio lleva business_id obligatorio, con FK e índice.
--  2. `tables` y `orders` ya incluyen location_id desde el día uno, aunque
--     hoy cada negocio tenga un solo local (evita una migración dolorosa en
--     la Etapa 5 de multi-sucursal).
--  3. Los precios de order_items se congelan al momento de la venta
--     (unit_price, discount_amount y hasta el nombre del producto quedan
--     "fotografiados" en la fila).
--  4. Todo id es un UUID de texto (CHAR(36)), generado en la aplicación
--     (@PrePersist, ver TenantScopedEntity/BaseEntity), nunca por la base —
--     mismo criterio que `@default(uuid())` en Prisma: evita IDs
--     secuenciales adivinables entre tenants (ataques IDOR).
--  5. order.idempotency_key + order.client_timestamp existen para la cola
--     offline de la Etapa 2 (roadmap) — ver @@unique(business_id, idempotency_key).
--
-- Diferencia deliberada de tipos frente a Prisma/Postgres: los enums de
-- schema.prisma (BusinessStatus, UserRole, etc.) se mapean acá a
-- VARCHAR + CHECK en vez de al tipo ENUM nativo de MySQL. MySQL sí tiene un
-- tipo ENUM de columna, pero reordenar/agregar valores exige un ALTER TABLE
-- que reescribe metadata de la columna y es más frágil entre versiones que
-- un CHECK — y CHECK (soportado y ENFORCED desde MySQL 8.0.16) mapea sin
-- fricción contra `@Enumerated(EnumType.STRING)` de Hibernate en el lado
-- Java. Ver com.cofeapp.backend.domain.enums.*.
--
-- ALCANCE DE ESTA MIGRACIÓN: se crea el esquema COMPLETO (igual que el
-- schema.prisma original, que ya incluía las tablas de la Etapa 2 en curso
-- — categories/products/discounts/tables/orders/order_items — aunque esa
-- lógica de negocio todavía no exista del lado Java). Por qué: es
-- exactamente el mismo principio de diseño 2 de arriba, aplicado a nivel de
-- ronda de migración — evita tener que volver a tocar la base cuando se
-- migre la Etapa 2. Las entidades JPA/servicios/controllers de esta primera
-- ronda cubren SOLO Etapa 1 (businesses, locations, users) — ver
-- docs/MIGRACION.md en la raíz del monorepo para el detalle completo.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- TENANT ROOT
-- ----------------------------------------------------------------------------

CREATE TABLE businesses (
  id               CHAR(36)     NOT NULL,
  legal_name       VARCHAR(150) NOT NULL,
  trade_name       VARCHAR(150) NULL,
  tax_id           VARCHAR(20)  NOT NULL,
  fiscal_condition VARCHAR(30)  NOT NULL,
  plan             VARCHAR(20)  NOT NULL DEFAULT 'TRIAL',
  status           VARCHAR(20)  NOT NULL DEFAULT 'TRIAL',
  created_at       DATETIME(6)  NOT NULL,
  updated_at       DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_businesses_tax_id (tax_id),
  CONSTRAINT chk_businesses_fiscal_condition
    CHECK (fiscal_condition IN ('MONOTRIBUTO', 'RESPONSABLE_INSCRIPTO', 'EXENTO')),
  CONSTRAINT chk_businesses_plan CHECK (plan IN ('TRIAL', 'BASIC', 'PRO', 'ENTERPRISE')),
  CONSTRAINT chk_businesses_status CHECK (status IN ('TRIAL', 'ACTIVE', 'SUSPENDED', 'CANCELLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE locations (
  id          CHAR(36)     NOT NULL,
  business_id CHAR(36)     NOT NULL,
  name        VARCHAR(150) NOT NULL,
  address     VARCHAR(255) NULL,
  phone       VARCHAR(50)  NULL,
  is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at  DATETIME(6)  NOT NULL,
  updated_at  DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_locations_business_id (business_id),
  CONSTRAINT fk_locations_business
    FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ----------------------------------------------------------------------------
-- USUARIOS / RBAC
-- ----------------------------------------------------------------------------

CREATE TABLE users (
  id            CHAR(36)     NOT NULL,
  business_id   CHAR(36)     NOT NULL,
  -- Nulo = acceso a todos los locales del negocio (típicamente OWNER/ADMIN).
  location_id   CHAR(36)     NULL,
  -- Único a nivel GLOBAL (no compuesto con business_id) — mismo criterio
  -- que el schema.prisma original: el login no pide primero "a qué negocio
  -- pertenecés".
  email         VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  first_name    VARCHAR(100) NOT NULL,
  last_name     VARCHAR(100) NOT NULL,
  role          VARCHAR(20)  NOT NULL,
  is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at    DATETIME(6)  NOT NULL,
  updated_at    DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_email (email),
  KEY idx_users_business_id (business_id),
  KEY idx_users_business_id_role (business_id, role),
  CONSTRAINT fk_users_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT fk_users_location FOREIGN KEY (location_id) REFERENCES locations (id) ON DELETE SET NULL,
  CONSTRAINT chk_users_role CHECK (role IN ('OWNER', 'ADMIN', 'SUPERVISOR', 'WAITER', 'KITCHEN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ----------------------------------------------------------------------------
-- CATÁLOGO (esquema listo para la Etapa 2 — sin entidades/servicios Java
-- todavía en esta ronda, ver la nota de alcance al principio del archivo)
-- ----------------------------------------------------------------------------

CREATE TABLE categories (
  id            CHAR(36)     NOT NULL,
  business_id   CHAR(36)     NOT NULL,
  name          VARCHAR(150) NOT NULL,
  description   VARCHAR(500) NULL,
  display_order INT          NOT NULL DEFAULT 0,
  is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at    DATETIME(6)  NOT NULL,
  updated_at    DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_categories_business_id (business_id),
  CONSTRAINT fk_categories_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE products (
  id          CHAR(36)      NOT NULL,
  business_id CHAR(36)      NOT NULL,
  category_id CHAR(36)      NOT NULL,
  name        VARCHAR(150)  NOT NULL,
  description VARCHAR(500)  NULL,
  sku         VARCHAR(50)   NULL,
  price       DECIMAL(10,2) NOT NULL,
  image_url   VARCHAR(500)  NULL,
  is_active   BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at  DATETIME(6)   NOT NULL,
  updated_at  DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  KEY idx_products_business_id (business_id),
  KEY idx_products_business_id_category_id (business_id, category_id),
  CONSTRAINT fk_products_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  -- ON DELETE RESTRICT: un producto ya vendido no puede desaparecer sin
  -- romper el historial de ventas (order_items lo referencia) — la baja
  -- siempre es lógica (is_active = false), nunca un DELETE físico.
  CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE discounts (
  id                     CHAR(36)      NOT NULL,
  business_id            CHAR(36)      NOT NULL,
  name                   VARCHAR(150)  NOT NULL,
  type                   VARCHAR(20)   NOT NULL,
  value                  DECIMAL(10,2) NOT NULL,
  requires_authorization BOOLEAN       NOT NULL DEFAULT FALSE,
  is_active              BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at             DATETIME(6)   NOT NULL,
  updated_at             DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  KEY idx_discounts_business_id (business_id),
  CONSTRAINT fk_discounts_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT chk_discounts_type CHECK (type IN ('PERCENTAGE', 'FIXED_AMOUNT'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ----------------------------------------------------------------------------
-- MESAS
-- ----------------------------------------------------------------------------

CREATE TABLE tables (
  id          CHAR(36)    NOT NULL,
  business_id CHAR(36)    NOT NULL,
  location_id CHAR(36)    NOT NULL,
  number      INT         NOT NULL,
  label       VARCHAR(50) NULL,
  capacity    INT         NULL,
  status      VARCHAR(20) NOT NULL DEFAULT 'FREE',
  is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
  created_at  DATETIME(6) NOT NULL,
  updated_at  DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_tables_location_number (location_id, number),
  KEY idx_tables_business_id (business_id),
  CONSTRAINT fk_tables_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT fk_tables_location FOREIGN KEY (location_id) REFERENCES locations (id) ON DELETE CASCADE,
  CONSTRAINT chk_tables_status CHECK (status IN ('FREE', 'OCCUPIED', 'RESERVED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ----------------------------------------------------------------------------
-- PEDIDOS
-- ----------------------------------------------------------------------------

CREATE TABLE orders (
  id               CHAR(36)      NOT NULL,
  business_id      CHAR(36)      NOT NULL,
  location_id      CHAR(36)      NOT NULL,
  table_id         CHAR(36)      NULL,
  created_by_id    CHAR(36)      NOT NULL,
  status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
  payment_method   VARCHAR(20)   NULL,
  subtotal         DECIMAL(10,2) NOT NULL DEFAULT 0,
  discount_total   DECIMAL(10,2) NOT NULL DEFAULT 0,
  total            DECIMAL(10,2) NOT NULL DEFAULT 0,
  notes            VARCHAR(1000) NULL,
  idempotency_key  VARCHAR(100)  NOT NULL,
  client_timestamp DATETIME(6)   NOT NULL,
  created_at       DATETIME(6)   NOT NULL,
  updated_at       DATETIME(6)   NOT NULL,
  closed_at        DATETIME(6)   NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_orders_business_idempotency (business_id, idempotency_key),
  KEY idx_orders_business_id (business_id),
  KEY idx_orders_business_location_status (business_id, location_id, status),
  KEY idx_orders_business_table (business_id, table_id),
  CONSTRAINT fk_orders_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT fk_orders_location FOREIGN KEY (location_id) REFERENCES locations (id) ON DELETE CASCADE,
  CONSTRAINT fk_orders_table FOREIGN KEY (table_id) REFERENCES tables (id) ON DELETE SET NULL,
  CONSTRAINT fk_orders_created_by FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
  CONSTRAINT chk_orders_status
    CHECK (status IN ('PENDING', 'IN_PREPARATION', 'READY', 'DELIVERED', 'CANCELLED')),
  CONSTRAINT chk_orders_payment_method
    CHECK (payment_method IS NULL OR payment_method IN ('CASH', 'TRANSFER', 'CARD', 'QR'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE order_items (
  id               CHAR(36)      NOT NULL,
  business_id      CHAR(36)      NOT NULL,
  order_id         CHAR(36)      NOT NULL,
  product_id       CHAR(36)      NOT NULL,
  product_name     VARCHAR(150)  NOT NULL,
  quantity         INT           NOT NULL,
  unit_price       DECIMAL(10,2) NOT NULL,
  discount_id      CHAR(36)      NULL,
  discount_amount  DECIMAL(10,2) NOT NULL DEFAULT 0,
  subtotal         DECIMAL(10,2) NOT NULL,
  notes            VARCHAR(500)  NULL,
  created_at       DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  KEY idx_order_items_business_id (business_id),
  KEY idx_order_items_order_id (order_id),
  KEY idx_order_items_business_product (business_id, product_id),
  CONSTRAINT fk_order_items_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
  CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT,
  CONSTRAINT fk_order_items_discount FOREIGN KEY (discount_id) REFERENCES discounts (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
