-- ============================================================================
-- COFFE_APP — V7__location_receipt_paper_width.sql (MySQL 8)
-- ============================================================================
-- Etapa 3, parte final: impresión térmica desde el navegador.
-- Fran eligió, respondiendo preguntas puntuales antes de esta migración:
--   - Imprimir DESDE EL NAVEGADOR (window.print sobre un ticket angosto,
--     con la impresora instalada en Windows) — sin agente local ESC/POS.
--   - Imprimir comanda de cocina/barra, ticket para el cliente (no
--     fiscal) y cierre de caja.
--   - Ancho de papel CONFIGURABLE POR LOCAL (cada local puede tener una
--     ticketera distinta).
--   - Comanda con botón, nunca automática.
--
-- Una sola columna nueva en locations. Solo se aceptan los dos anchos
-- estándar de rollo térmico (58 mm y 80 mm); default 80 mm, el más común
-- en cafeterías — así todo local ya existente queda andando sin tocar nada.
-- ============================================================================

ALTER TABLE locations
  ADD COLUMN receipt_paper_width_mm SMALLINT NOT NULL DEFAULT 80 AFTER phone,
  ADD CONSTRAINT chk_locations_receipt_paper_width CHECK (receipt_paper_width_mm IN (58, 80));
