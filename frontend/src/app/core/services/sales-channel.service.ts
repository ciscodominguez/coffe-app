import { Injectable, inject } from '@angular/core';
import { SalesChannel, SaveSalesChannelInput } from '../models/sales-channel.model';
import { ApiService } from './api.service';

/** Apps de delivery de cada local (V19) — ver {@code SalesChannelController}. */
@Injectable({ providedIn: 'root' })
export class SalesChannelService {
  private readonly api = inject(ApiService);

  findAll(locationId: string): Promise<SalesChannel[]> {
    return this.api.get<SalesChannel[]>('/sales-channels', { locationId });
  }

  create(input: SaveSalesChannelInput): Promise<SalesChannel> {
    return this.api.post<SalesChannel>('/sales-channels', input);
  }

  update(id: string, input: SaveSalesChannelInput): Promise<SalesChannel> {
    return this.api.patch<SalesChannel>(`/sales-channels/${id}`, input);
  }
}
