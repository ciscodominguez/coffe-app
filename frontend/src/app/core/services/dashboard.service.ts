import { Injectable } from '@angular/core';
import { DashboardSummaryResponse } from '../models/dashboard.model';
import { ApiService } from './api.service';

/** Bloque E — ver el Javadoc de `DashboardController.java`/`DashboardService.java` del backend. */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  constructor(private readonly api: ApiService) {}

  /** `from`/`to` viajan como ISO-8601 (`Date#toISOString`) — el backend los parsea con `Instant.parse`. */
  summary(locationId: string, from: Date, to: Date): Promise<DashboardSummaryResponse> {
    return this.api.get<DashboardSummaryResponse>('/dashboard/summary', {
      locationId,
      from: from.toISOString(),
      to: to.toISOString(),
    });
  }
}
