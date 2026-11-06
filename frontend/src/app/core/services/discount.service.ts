import { Injectable } from '@angular/core';
import { DiscountResponse, SaveDiscountInput } from '../models/discount.model';
import { ApiService } from './api.service';

/** Lista de descuentos del negocio (V12) — ver DiscountController del backend. */
@Injectable({ providedIn: 'root' })
export class DiscountService {
  constructor(private readonly api: ApiService) {}

  findAll(): Promise<DiscountResponse[]> {
    return this.api.get<DiscountResponse[]>('/discounts');
  }

  create(input: SaveDiscountInput): Promise<DiscountResponse> {
    return this.api.post<DiscountResponse>('/discounts', input);
  }

  update(id: string, input: SaveDiscountInput): Promise<DiscountResponse> {
    return this.api.put<DiscountResponse>(`/discounts/${id}`, input);
  }

  /** Baja lógica: deja de ofrecerse al cobrar. Se reactiva editándolo. */
  deactivate(id: string): Promise<void> {
    return this.api.delete<void>(`/discounts/${id}`);
  }
}
