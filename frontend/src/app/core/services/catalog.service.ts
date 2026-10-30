import { Injectable } from '@angular/core';
import { CategoryResponse, CreateCategoryInput, UpdateCategoryInput } from '../models/category.model';
import { CreateProductInput, ProductResponse, UpdateProductInput } from '../models/product.model';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class CategoryService {
  constructor(private readonly api: ApiService) {}

  findAll(): Promise<CategoryResponse[]> {
    return this.api.get<CategoryResponse[]>('/categories');
  }

  create(input: CreateCategoryInput): Promise<CategoryResponse> {
    return this.api.post<CategoryResponse>('/categories', input);
  }

  update(id: string, input: UpdateCategoryInput): Promise<CategoryResponse> {
    return this.api.patch<CategoryResponse>(`/categories/${id}`, input);
  }

  /** Baja lógica (isActive = false) — mismo criterio que CategoryController#deactivate en el backend, nunca un delete físico. */
  deactivate(id: string): Promise<void> {
    return this.api.delete<void>(`/categories/${id}`);
  }
}

@Injectable({ providedIn: 'root' })
export class ProductService {
  constructor(private readonly api: ApiService) {}

  findAll(): Promise<ProductResponse[]> {
    return this.api.get<ProductResponse[]>('/products');
  }

  create(input: CreateProductInput): Promise<ProductResponse> {
    return this.api.post<ProductResponse>('/products', input);
  }

  update(id: string, input: UpdateProductInput): Promise<ProductResponse> {
    return this.api.patch<ProductResponse>(`/products/${id}`, input);
  }

  /** Baja lógica (isActive = false) — mismo criterio que ProductController#deactivate en el backend, nunca un delete físico. */
  deactivate(id: string): Promise<void> {
    return this.api.delete<void>(`/products/${id}`);
  }
}
