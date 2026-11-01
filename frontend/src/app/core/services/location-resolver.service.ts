import { Injectable, inject } from '@angular/core';
import { AuthService } from './auth.service';
import { LocationService } from './business.service';

/**
 * Local "actual" de quien está logueado — el mismo criterio que ya usaban
 * Pedidos/Cocina/Caja/Insumos cada una por su cuenta (ver el Javadoc de
 * WebSocketAuthHandshakeInterceptor del backend): si el JWT trae un local
 * fijo, ese; si no (OWNER/ADMIN), el más antiguo del negocio.
 */
@Injectable({ providedIn: 'root' })
export class LocationResolverService {
  private readonly authService = inject(AuthService);
  private readonly locationService = inject(LocationService);

  async resolve(): Promise<string> {
    const user = this.authService.user();
    if (user?.locationId) return user.locationId;

    const locations = await this.locationService.findAll();
    if (locations.length === 0) {
      throw new Error('Este negocio todavía no tiene ningún local cargado.');
    }
    return locations[0].id;
  }
}
