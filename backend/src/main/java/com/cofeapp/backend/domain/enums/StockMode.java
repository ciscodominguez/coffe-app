package com.cofeapp.backend.domain.enums;

/**
 * Cómo un {@code Product} maneja su stock — Bloque D (ítems 12/13). Cada
 * producto elige uno de los tres, nunca combina modos:
 *
 * <ul>
 *   <li>{@code NONE} — sin control de stock (default, comportamiento
 *       previo al Bloque D, ej. un café con leche cuyo insumo principal es
 *       agua caliente y no vale la pena trackear).
 *   <li>{@code DIRECT} — "producto terminado": el producto tiene su
 *       propio stock por local (ver {@code ProductStock}), se descuenta 1
 *       a 1 por unidad vendida. Para ítems simples ya armados —
 *       medialunas, sandwiches — que Fran pidió explícitamente poder
 *       trackear sin pasar por una receta.
 *   <li>{@code RECIPE} — el producto consume insumos según una receta
 *       (ver {@code ProductRecipeItem}): vender N unidades descuenta
 *       {@code quantityPerUnit * N} de cada insumo de la receta en
 *       {@code IngredientStock}. Para ítems preparados — un café cortado
 *       consume café en grano + leche.
 * </ul>
 *
 * Ver {@code StockService#consumeForOrder}/{@code #restoreForOrder} sobre
 * cuándo se descuenta/repone, y el Javadoc de {@code Product} sobre por
 * qué {@code stockMode}/{@code minStock} viven como columnas sueltas en
 * vez de una tabla de configuración aparte.
 */
public enum StockMode {
  NONE,
  DIRECT,
  RECIPE
}
