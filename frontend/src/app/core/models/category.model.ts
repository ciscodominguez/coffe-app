/** Espejo de {@code category/dto/CategoryResponse.java} del backend. */
export interface CategoryResponse {
  id: string;
  name: string;
  description: string | null;
  displayOrder: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

/** Espejo de {@code category/dto/CreateCategoryRequest.java}. */
export interface CreateCategoryInput {
  name: string;
  description?: string | null;
  displayOrder?: number;
}

/**
 * Espejo de {@code category/dto/UpdateCategoryRequest.java} — todos los
 * campos opcionales, mismo criterio de "patch parcial" que el backend.
 */
export interface UpdateCategoryInput {
  name?: string;
  description?: string | null;
  displayOrder?: number;
  isActive?: boolean;
}
