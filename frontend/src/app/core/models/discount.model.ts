import { DiscountType } from './order.model';

/** Un descuento de la lista del negocio (V12) — espejo de discount/dto/DiscountResponse. */
export interface DiscountResponse {
  id: string;
  name: string;
  type: DiscountType;
  value: number;
  /** Solo supervisor, administrador o dueño lo pueden aplicar. */
  requiresAuthorization: boolean;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface SaveDiscountInput {
  name: string;
  type: DiscountType;
  value: number;
  requiresAuthorization: boolean;
  isActive?: boolean;
}
