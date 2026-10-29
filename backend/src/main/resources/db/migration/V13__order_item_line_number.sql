-- ============================================================================
-- COFFE_APP — V13__order_item_line_number.sql (MySQL 8)
-- ============================================================================
-- Orden fijo de los productos dentro de un pedido.
--
-- Hasta ahora se ordenaban por `created_at`. En Windows varios ítems del
-- mismo pedido pueden quedar con el MISMO instante (el reloj no llega a
-- cambiar entre uno y otro), y entonces MySQL los devolvía en cualquier
-- orden: el ticket, Cocina o la ventana de cobro podían mostrarlos
-- "mezclados" entre una lectura y otra. Lo detectó un test en la PC de Fran.
--
-- `line_number` es el número de renglón dentro del pedido (1, 2, 3…, y lo
-- agregado después sigue la numeración). Los pedidos viejos quedan en 0 y
-- siguen ordenándose por `created_at` como antes.
-- ============================================================================

ALTER TABLE order_items
  ADD COLUMN line_number INT NOT NULL DEFAULT 0 AFTER batch;
