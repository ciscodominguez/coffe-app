import { Injectable, inject } from '@angular/core';
import { LocationResponse } from '../models/business.model';
import { BusinessService, LocationService } from './business.service';
import { TicketContext } from './ticket-templates';

/**
 * Encabezado de los tickets (nombre del negocio + local con su ancho de
 * ticketera), compartido por las pantallas que imprimen (Pedidos, Cocina).
 * Se carga una sola vez por local y queda en memoria. Nunca falla: si no
 * se pudo cargar, los tickets salen igual con el ancho por defecto.
 */
@Injectable({ providedIn: 'root' })
export class PrintContextService {
  private readonly businessService = inject(BusinessService);
  private readonly locationService = inject(LocationService);
  private readonly cache = new Map<string, Promise<TicketContext>>();

  get(locationId: string): Promise<TicketContext> {
    let pending = this.cache.get(locationId);
    if (!pending) {
      pending = this.load(locationId);
      this.cache.set(locationId, pending);
    }
    return pending;
  }

  private async load(locationId: string): Promise<TicketContext> {
    try {
      const [business, locations] = await Promise.all([
        this.businessService.getDisplayName(),
        this.locationService.findAll(),
      ]);
      return {
        businessName: business.displayName,
        location: locations.find((location: LocationResponse) => location.id === locationId) ?? null,
      };
    } catch {
      this.cache.delete(locationId);
      return { businessName: '', location: null };
    }
  }
}
