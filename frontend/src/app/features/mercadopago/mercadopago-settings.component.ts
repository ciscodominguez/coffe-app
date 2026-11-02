import { Component, OnInit, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  ConfigureLocationQrInput,
  LocationQrStatus,
  MercadoPagoStatus,
  QR_MODE_LABELS,
  QrMode,
} from '../../core/models/mercadopago.model';
import { extractErrorMessage } from '../../core/services/error.util';
import { MercadoPagoService } from '../../core/services/mercadopago.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

/** Formulario de un local (se arma uno por local de la lista). */
interface LocationForm {
  qrMode: QrMode;
  streetName: string;
  streetNumber: string;
  cityName: string;
  stateName: string;
  /** "latitud, longitud" tal cual se copia de Google Maps. */
  coordinates: string;
  reference: string;
}

/**
 * "Cobros con QR" (paso 2.2 de la hoja de ruta) — solo dueño y
 * administrador:
 *  1. Conectar la cuenta de Mercado Pago pegando su Access Token.
 *  2. Configurar el QR de cada local: la primera vez pide la dirección
 *     (Mercado Pago la exige para crear la sucursal y la caja) y después
 *     solo se elige QR en pantalla o QR impreso.
 */
@Component({
    selector: 'app-mercadopago-settings',
    imports: [FormsModule, IconComponent],
    templateUrl: './mercadopago-settings.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './mercadopago-settings.component.css'
})
export class MercadoPagoSettingsComponent implements OnInit {
  private readonly mercadoPago = inject(MercadoPagoService);
  private readonly toast = inject(ToastService);

  readonly modeLabels = QR_MODE_LABELS;
  readonly modes: readonly QrMode[] = ['DYNAMIC', 'STATIC'];

  readonly loading = signal(true);
  readonly status = signal<MercadoPagoStatus | null>(null);
  readonly error = signal<string | null>(null);

  // Conexión
  readonly token = signal('');
  readonly showToken = signal(false);
  readonly changingToken = signal(false);
  readonly connecting = signal(false);
  readonly confirmDisconnect = signal(false);
  readonly disconnecting = signal(false);

  // Locales
  readonly forms = signal<Record<string, LocationForm>>({});
  readonly savingLocation = signal<string | null>(null);
  readonly locationErrors = signal<Record<string, string>>({});

  async ngOnInit(): Promise<void> {
    try {
      this.apply(await this.mercadoPago.status());
    } catch (error) {
      this.error.set(extractErrorMessage(error, 'No se pudo cargar la configuración de Mercado Pago.'));
    } finally {
      this.loading.set(false);
    }
  }

  async connect(): Promise<void> {
    const token = this.token().trim();
    if (!token || this.connecting()) return;
    this.connecting.set(true);
    this.error.set(null);
    try {
      this.apply(await this.mercadoPago.connect(token));
      this.token.set('');
      this.changingToken.set(false);
      this.toast.success('Mercado Pago conectado.');
    } catch (error) {
      this.error.set(extractErrorMessage(error, 'No se pudo conectar Mercado Pago.'));
    } finally {
      this.connecting.set(false);
    }
  }

  async disconnect(): Promise<void> {
    if (this.disconnecting()) return;
    this.disconnecting.set(true);
    this.error.set(null);
    try {
      this.apply(await this.mercadoPago.disconnect());
      this.confirmDisconnect.set(false);
      this.toast.info('Mercado Pago desconectado. Ya no se puede cobrar con QR.');
    } catch (error) {
      this.error.set(extractErrorMessage(error, 'No se pudo desconectar Mercado Pago.'));
    } finally {
      this.disconnecting.set(false);
    }
  }

  form(locationId: string): LocationForm {
    return this.forms()[locationId];
  }

  setField(locationId: string, field: keyof LocationForm, value: string): void {
    this.forms.update((all) => ({ ...all, [locationId]: { ...all[locationId], [field]: value } }));
  }

  async saveLocation(location: LocationQrStatus): Promise<void> {
    if (this.savingLocation()) return;
    const f = this.form(location.locationId);
    const input: ConfigureLocationQrInput = { qrMode: f.qrMode };

    if (!location.configured) {
      const coordinates = parseCoordinates(f.coordinates);
      if (!coordinates) {
        this.setLocationError(
          location.locationId,
          'Pegá la ubicación del local como "latitud, longitud" (por ejemplo: -38.0055, -57.5426).',
        );
        return;
      }
      Object.assign(input, {
        streetName: f.streetName.trim(),
        streetNumber: f.streetNumber.trim(),
        cityName: f.cityName.trim(),
        stateName: f.stateName.trim(),
        latitude: coordinates.latitude,
        longitude: coordinates.longitude,
        reference: f.reference.trim() || undefined,
      });
    }

    this.savingLocation.set(location.locationId);
    this.setLocationError(location.locationId, null);
    try {
      this.apply(await this.mercadoPago.configureLocation(location.locationId, input));
      this.toast.success(location.configured ? 'Guardado.' : `QR de ${location.locationName} listo para cobrar.`);
    } catch (error) {
      this.setLocationError(location.locationId, extractErrorMessage(error, 'No se pudo guardar.'));
    } finally {
      this.savingLocation.set(null);
    }
  }

  modeChanged(location: LocationQrStatus): boolean {
    return this.form(location.locationId)?.qrMode !== location.qrMode;
  }

  private setLocationError(locationId: string, message: string | null): void {
    this.locationErrors.update((all) => {
      const next = { ...all };
      if (message) next[locationId] = message;
      else delete next[locationId];
      return next;
    });
  }

  /** Guarda el estado nuevo y arma (o actualiza) el formulario de cada local sin pisar lo que se estaba tipeando. */
  private apply(status: MercadoPagoStatus): void {
    this.status.set(status);
    this.forms.update((current) => {
      const next: Record<string, LocationForm> = {};
      for (const location of status.locations) {
        const previous = current[location.locationId];
        next[location.locationId] = previous
          ? { ...previous, qrMode: location.qrMode }
          : {
              qrMode: location.qrMode,
              streetName: '',
              streetNumber: '',
              cityName: '',
              stateName: 'Buenos Aires',
              coordinates: '',
              reference: '',
            };
      }
      return next;
    });
  }
}

/** Acepta "-38.0055, -57.5426" (lo que copia Google Maps) o con espacios/punto y coma. */
function parseCoordinates(text: string): { latitude: number; longitude: number } | null {
  const parts = text
    .replace(/[()]/g, '')
    .split(/[,;\s]+/)
    .map((part) => part.trim())
    .filter(Boolean);
  if (parts.length !== 2) return null;
  const latitude = Number(parts[0]);
  const longitude = Number(parts[1]);
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null;
  if (Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return null;
  return { latitude, longitude };
}
