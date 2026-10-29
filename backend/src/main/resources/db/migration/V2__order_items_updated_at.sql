-- ============================================================================
-- COFE_APP — V2__order_items_updated_at.sql (MySQL 8)
-- ============================================================================
-- Agrega `updated_at` a `order_items`, que V1 no incluía (la migración
-- original la calcó de un modelo Prisma sin @updatedAt en OrderItem, por
-- ser conceptualmente un registro de solo-alta — precios congelados al
-- momento de la venta, nunca editados después).
--
-- Se agrega igual acá para que la entidad OrderItem del lado Java pueda
-- reusar la misma jerarquía tenant-scoped (BaseEntity/TenantScopedEntity)
-- que el resto de las tablas, en vez de necesitar una jerarquía paralela:
-- con Hibernate 6, declarar un @FilterDef nuevo y separado para esa
-- jerarquía paralela volvería a chocar contra el mismo "Multiple
-- @FilterDef" que ya se resolvió en la Etapa 1 (ver el Javadoc de
-- TenantScopedEntity) — el @FilterDef de aislamiento multi-tenant tiene
-- que seguir viviendo en un único lugar de toda la jerarquía de entidades.
--
-- A nivel de aplicación, OrderItem sigue siendo de solo-alta: no existe
-- ningún endpoint de edición sobre un ítem ya creado, así que esta columna
-- en la práctica nunca cambia después del insert inicial.
-- ============================================================================

ALTER TABLE order_items
  ADD COLUMN updated_at DATETIME(6) NOT NULL AFTER created_at;
