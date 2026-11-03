import { Injectable } from '@angular/core';
import { ProductResponse } from '../models/product.model';
import {
  AdjustStockInput,
  CreateIngredientInput,
  IngredientResponse,
  IngredientStockLevelResponse,
  ProductRecipeItemResponse,
  ProductStockLevelResponse,
  StockAdjustmentResponse,
  UpdateIngredientInput,
  UpdateProductStockConfigInput,
} from '../models/stock.model';
import { ApiService } from './api.service';

/** Bloque D de las mejoras pedidas (ítems 12/13) — ver el Javadoc de `StockController.java` del backend. */
@Injectable({ providedIn: 'root' })
export class StockService {
  constructor(private readonly api: ApiService) {}

  // ---- Insumos ----

  findAllIngredients(): Promise<IngredientResponse[]> {
    return this.api.get<IngredientResponse[]>('/stock/ingredients');
  }

  createIngredient(input: CreateIngredientInput): Promise<IngredientResponse> {
    return this.api.post<IngredientResponse>('/stock/ingredients', input);
  }

  updateIngredient(id: string, input: UpdateIngredientInput): Promise<IngredientResponse> {
    return this.api.patch<IngredientResponse>(`/stock/ingredients/${id}`, input);
  }

  ingredientLevels(locationId: string): Promise<IngredientStockLevelResponse[]> {
    return this.api.get<IngredientStockLevelResponse[]>('/stock/ingredients/levels', { locationId });
  }

  adjustIngredientStock(id: string, input: AdjustStockInput): Promise<StockAdjustmentResponse> {
    return this.api.post<StockAdjustmentResponse>(`/stock/ingredients/${id}/adjust`, input);
  }

  ingredientAdjustmentHistory(id: string, locationId: string): Promise<StockAdjustmentResponse[]> {
    return this.api.get<StockAdjustmentResponse[]>(`/stock/ingredients/${id}/adjustments`, { locationId });
  }

  // ---- Productos (stock DIRECT + receta) ----

  productLevels(locationId: string): Promise<ProductStockLevelResponse[]> {
    return this.api.get<ProductStockLevelResponse[]>('/stock/products/levels', { locationId });
  }

  productRecipe(productId: string): Promise<ProductRecipeItemResponse[]> {
    return this.api.get<ProductRecipeItemResponse[]>(`/stock/products/${productId}/recipe`);
  }

  updateProductStockConfig(productId: string, input: UpdateProductStockConfigInput): Promise<ProductResponse> {
    return this.api.patch<ProductResponse>(`/stock/products/${productId}/config`, input);
  }

  adjustProductStock(productId: string, input: AdjustStockInput): Promise<StockAdjustmentResponse> {
    return this.api.post<StockAdjustmentResponse>(`/stock/products/${productId}/adjust`, input);
  }

  productAdjustmentHistory(productId: string, locationId: string): Promise<StockAdjustmentResponse[]> {
    return this.api.get<StockAdjustmentResponse[]>(`/stock/products/${productId}/adjustments`, { locationId });
  }
}
