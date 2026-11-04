import { Injectable } from '@angular/core';
import { OptionGroupResponse, SaveOptionGroupInput } from '../models/option.model';
import { ApiService } from './api.service';

/** Grupos de opciones (V11) — ver OptionGroupController del backend. */
@Injectable({ providedIn: 'root' })
export class OptionGroupService {
  constructor(private readonly api: ApiService) {}

  findAll(): Promise<OptionGroupResponse[]> {
    return this.api.get<OptionGroupResponse[]>('/option-groups');
  }

  create(input: SaveOptionGroupInput): Promise<OptionGroupResponse> {
    return this.api.post<OptionGroupResponse>('/option-groups', input);
  }

  update(id: string, input: SaveOptionGroupInput): Promise<OptionGroupResponse> {
    return this.api.put<OptionGroupResponse>(`/option-groups/${id}`, input);
  }

  /** Orden en que se preguntan al tomar el pedido: todos los ids, en el orden nuevo. */
  reorder(groupIds: string[]): Promise<OptionGroupResponse[]> {
    return this.api.put<OptionGroupResponse[]>('/option-groups/order', { groupIds });
  }

  delete(id: string): Promise<void> {
    return this.api.delete<void>(`/option-groups/${id}`);
  }

  /** Desde el producto: qué grupos usa (reemplaza la lista). */
  setProductGroups(productId: string, groupIds: string[]): Promise<{ groupIds: string[] }> {
    return this.api.put<{ groupIds: string[] }>(`/products/${productId}/option-groups`, { groupIds });
  }
}
