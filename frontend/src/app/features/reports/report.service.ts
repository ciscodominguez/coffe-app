import { Injectable } from '@angular/core';
import { ApiService } from '../../core/services/api.service';

/** Reporte en Excel para el contador (paso 2.6) — ver ReportController del backend. */
@Injectable({ providedIn: 'root' })
export class ReportService {
  constructor(private readonly api: ApiService) {}

  excel(from: Date, to: Date, locationId: string | null): Promise<Blob> {
    return this.api.getBlob('/reports/excel', {
      from: from.toISOString(),
      to: to.toISOString(),
      locationId: locationId ?? undefined,
      tz: Intl.DateTimeFormat().resolvedOptions().timeZone,
    });
  }
}
