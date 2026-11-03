/**
 * Espejo de `stock/dto/*.java` del backend — Bloque D de las mejoras
 * pedidas (ítems 12/13, "manejo de stock"). Mismo criterio de nombres que
 * `cash-session.model.ts`: un `*Response` por cada DTO de respuesta, un
 * `*Input` por cada DTO de request.
 */

/** Espejo de {@code stock/dto/IngredientResponse.java} — un insumo del catálogo del negocio (ej. "Café en grano"). */
export interface IngredientResponse {
  id: string;
  name: string;
  unit: string;
  minStock: number | null;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

/** Espejo de {@code stock/dto/CreateIngredientRequest.java}. */
export interface CreateIngredientInput {
  name: string;
  unit: string;
  minStock?: number | null;
}

/** Espejo de {@code stock/dto/UpdateIngredientRequest.java} — todos los campos opcionales, mismo criterio de "patch parcial" que el resto del catálogo. */
export interface UpdateIngredientInput {
  name?: string;
  unit?: string;
  minStock?: number | null;
  isActive?: boolean;
}

/**
 * Espejo de {@code stock/dto/IngredientStockLevelResponse.java} — cantidad
 * ACTUAL de un insumo en un local puntual. `currentStock` es 0 cuando el
 * insumo todavía no se tocó en ese local (nunca un error). `belowMinimum`
 * ya viene calculado por el backend — es la marca en rojo que Fran pidió
 * en vez de trabar la venta.
 */
export interface IngredientStockLevelResponse {
  ingredientId: string;
  ingredientName: string;
  unit: string;
  minStock: number | null;
  currentStock: number;
  belowMinimum: boolean;
}

/** Espejo de {@code stock/dto/ProductStockLevelResponse.java} — mismo criterio que `IngredientStockLevelResponse`, para productos en modo DIRECT. */
export interface ProductStockLevelResponse {
  productId: string;
  productName: string;
  minStock: number | null;
  currentStock: number;
  belowMinimum: boolean;
}

/**
 * Espejo de {@code stock/dto/AdjustStockRequest.java} — ajuste manual: se
 * manda la cantidad REAL contada (no un delta a mano), mismo criterio que
 * el conteo de efectivo de la Caja. El backend calcula el delta.
 */
export interface AdjustStockInput {
  locationId: string;
  newQuantity: number;
  reason?: string | null;
}

/** Espejo de {@code stock/dto/StockAdjustmentResponse.java} — un ajuste manual ya aplicado, con "nombre real" de quién lo hizo. */
export interface StockAdjustmentResponse {
  id: string;
  locationId: string;
  previousQuantity: number;
  newQuantity: number;
  delta: number;
  reason: string | null;
  createdById: string;
  createdByName: string | null;
  createdAt: string;
}

/** Espejo de {@code stock/dto/RecipeItemInput.java} — una línea de receta a guardar. */
export interface RecipeItemInput {
  ingredientId: string;
  quantityPerUnit: number;
}

/** Espejo de {@code stock/dto/ProductRecipeItemResponse.java} — una línea de receta ya guardada, con el nombre/unidad del insumo resuelto. */
export interface ProductRecipeItemResponse {
  ingredientId: string;
  ingredientName: string;
  unit: string;
  quantityPerUnit: number;
}

/**
 * Espejo de {@code stock/dto/UpdateProductStockConfigRequest.java} —
 * `recipeItems` solo se usa (y reemplaza la receta completa) cuando
 * `stockMode = 'RECIPE'`; en `DIRECT`/`NONE` se ignora si viene.
 */
export interface UpdateProductStockConfigInput {
  stockMode: 'NONE' | 'DIRECT' | 'RECIPE';
  minStock?: number | null;
  recipeItems?: RecipeItemInput[];
}
