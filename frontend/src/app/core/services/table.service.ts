import { Injectable } from '@angular/core';
import { CreateTableInput, TableResponse, UpdateTableInput } from '../models/table.model';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class TableService {
  constructor(private readonly api: ApiService) {}

  findAllByLocation(locationId: string): Promise<TableResponse[]> {
    return this.api.get<TableResponse[]>('/tables', { locationId });
  }

  create(input: CreateTableInput): Promise<TableResponse> {
    return this.api.post<TableResponse>('/tables', input);
  }

  update(id: string, input: UpdateTableInput): Promise<TableResponse> {
    return this.api.patch<TableResponse>(`/tables/${id}`, input);
  }

  deactivate(id: string): Promise<void> {
    return this.api.delete<void>(`/tables/${id}`);
  }
}
