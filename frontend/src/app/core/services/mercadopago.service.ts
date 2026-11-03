import { PaymentItemInput } from '../models/order.model';
import { Injectable } from '@angular/core';
import {
  ConfigureLocationQrInput,
  MercadoPagoStatus,
  QrAvailability,
  QrChargeResponse,
} from '../models/mercadopago.model';
import { ApiService } from './api.service';

/** Cobros con QR de Mercado Pago (backend: MercadoPagoController / QrChargeController). */
@Injectable({ providedIn: 'root' })
export class MercadoPagoService {
  constructor(private readonly api: ApiService) {}

  // --- Configuración (dueño / administrador) ---

  status(): Promise<MercadoPagoStatus> {
    return this.api.get<MercadoPagoStatus>('/mercadopago/account');
  }

  connect(accessToken: string): Promise<MercadoPagoStatus> {
    return this.api.post<MercadoPagoStatus>('/mercadopago/account', { accessToken });
  }

  disconnect(): Promise<MercadoPagoStatus> {
    return this.api.delete<MercadoPagoStatus>('/mercadopago/account');
  }

  configureLocation(locationId: string, input: ConfigureLocationQrInput): Promise<MercadoPagoStatus> {
    return this.api.patch<MercadoPagoStatus>(`/mercadopago/locations/${locationId}`, input);
  }

  // --- Cobro (quien cobra) ---

  availability(locationId: string): Promise<QrAvailability> {
    return this.api.get<QrAvailability>('/mercadopago/availability', { locationId });
  }

  /** Sin monto = todo lo que falta. Si el pedido ya tiene un QR esperando, devuelve ese. */
  /** Sin monto: todo el saldo. Con `items` (V14, cuenta dividida por productos): lo de esa persona. */
  createCharge(orderId: string, amount?: number, items?: PaymentItemInput[]): Promise<QrChargeResponse> {
    const body = items && items.length > 0 ? { items } : amount ? { amount } : {};
    return this.api.post<QrChargeResponse>(`/orders/${orderId}/qr-charges`, body);
  }

  /** Además de leer, le pregunta a Mercado Pago si ya se pagó. */
  getCharge(id: string): Promise<QrChargeResponse> {
    return this.api.get<QrChargeResponse>(`/qr-charges/${id}`);
  }

  cancelCharge(id: string): Promise<QrChargeResponse> {
    return this.api.post<QrChargeResponse>(`/qr-charges/${id}/cancel`);
  }
}
