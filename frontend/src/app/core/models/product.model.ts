/**
 * Espejo de {@code domain/enums/StockMode.java} — Bloque D (ítems 12/13).
 * Cada producto elige uno: `NONE` (sin control), `DIRECT` (stock propio,
 * ítems simples ya armados como medialunas/sanguches) o `RECIPE` (consume
 * insumos según una receta, ver `ProductRecipeItemResponse`).
 */
export type StockMode = 'NONE' | 'DIRECT' | 'RECIPE';

export const STOCK_MODE_LABELS: Readonly<Record<StockMode, string>> = {
  NONE: 'Sin control de stock',
  DIRECT: 'Stock propio (producto terminado)',
  RECIPE: 'Por receta (consume insumos)',
};

/** Espejo de {@code product/dto/ProductResponse.java} del backend. */
export interface ProductResponse {
  id: string;
  categoryId: string;
  name: string;
  description: string | null;
  ingredients: string | null;
  sku: string | null;
  price: number;
  imageUrl: string | null;
  isActive: boolean;
  /** Bloque D — ver `StockMode`. `minStock` solo aplica en modo DIRECT (en RECIPE el umbral vive en cada insumo de la receta). */
  stockMode: StockMode;
  minStock: number | null;
  createdAt: string;
  updatedAt: string;
}

/** Espejo de {@code product/dto/CreateProductRequest.java}. */
export interface CreateProductInput {
  categoryId: string;
  name: string;
  description?: string | null;
  ingredients?: string | null;
  sku?: string | null;
  price: number;
  imageUrl?: string | null;
}

/**
 * Espejo de {@code product/dto/UpdateProductRequest.java} — todos los
 * campos opcionales, mismo criterio de "patch parcial" que el backend.
 */
export interface UpdateProductInput {
  categoryId?: string;
  name?: string;
  description?: string | null;
  ingredients?: string | null;
  sku?: string | null;
  price?: number;
  imageUrl?: string | null;
  isActive?: boolean;
}
