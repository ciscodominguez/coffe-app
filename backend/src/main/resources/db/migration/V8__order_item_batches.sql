-- ============================================================================
-- COFFE_APP — V8__order_item_batches.sql (MySQL 8)
-- ============================================================================
-- Hoja de ruta (README, sección 16), paso 1: agregar productos a un pedido
-- que ya está abierto ("la mesa pide otro cortado").
--
-- Cada vez que se le suman ítems a un pedido abierto, esos ítems entran
-- como una "tanda" nueva (batch 2, 3…). Así Cocina sabe qué es lo recién
-- agregado (lo muestra marcado "+ Agregado" y la comanda imprime solo eso)
-- y qué ya se había preparado antes (lo muestra atenuado). Decisión de
-- Fran: el pedido vuelve a la columna "Nuevo" de Cocina cuando le agregan
-- algo, para que nadie se lo pierda.
--
-- Todos los ítems que ya existen quedan en la tanda 1 (el DEFAULT), así
-- que los pedidos cargados antes de esta migración se ven igual que antes.
--
-- INT (no SMALLINT) a propósito: con SMALLINT, Hibernate valida contra
-- INTEGER para un Integer y frena el arranque (mismo problema que tuvo
-- receipt_paper_width_mm en la V7).
-- ============================================================================

ALTER TABLE order_items
  ADD COLUMN batch INT NOT NULL DEFAULT 1 AFTER notes,
  ADD CONSTRAINT chk_order_items_batch CHECK (batch >= 1);
