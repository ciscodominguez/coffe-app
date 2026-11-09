import { Injectable } from '@angular/core';
import { FloorArea, FloorPlan, SaveLayoutInput } from '../models/floor.model';
import { ApiService } from './api.service';

/** Plano del salón (V18) — ver {@code FloorController} del backend. */
@Injectable({ providedIn: 'root' })
export class FloorService {
  constructor(private readonly api: ApiService) {}

  plan(locationId: string): Promise<FloorPlan> {
    return this.api.get<FloorPlan>('/floor', { locationId });
  }

  createArea(locationId: string, name: string): Promise<FloorArea> {
    return this.api.post<FloorArea>('/floor/areas', { locationId, name });
  }

  renameArea(id: string, name: string): Promise<FloorArea> {
    return this.api.patch<FloorArea>(`/floor/areas/${id}`, { name });
  }

  deleteArea(id: string): Promise<void> {
    return this.api.delete<void>(`/floor/areas/${id}`);
  }

  saveLayout(areaId: string, input: SaveLayoutInput): Promise<FloorPlan> {
    return this.api.put<FloorPlan>(`/floor/areas/${areaId}/layout`, input);
  }
}
