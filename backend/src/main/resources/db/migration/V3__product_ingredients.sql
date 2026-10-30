-- ============================================================================
-- COFFE_APP — V3__product_ingredients.sql (MySQL 8)
-- ============================================================================
-- Ítem 10 de las mejoras pedidas: campo simple de ingredientes por
-- producto, texto libre y sin tabla aparte — deliberadamente "chiquito",
-- como se pidió. Si en el futuro hace falta modelarlo como una lista
-- estructurada (con alérgenos, cantidades, etc.), esa es una migración
-- aparte que reemplazaría esta columna por una tabla product_ingredients.
-- ============================================================================

ALTER TABLE products
  ADD COLUMN ingredients VARCHAR(1000) NULL AFTER description;
