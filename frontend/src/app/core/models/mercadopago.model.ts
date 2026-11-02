import { OrderResponse } from './order.model';

/** Cómo paga el cliente con QR de Mercado Pago en un local (backend: QrMode). */
export type QrMode = 'DYNAMIC' | 'STATIC';

export const QR_MODE_LABELS: Readonly<Record<QrMode, string>> = {
  DYNAMIC: 'QR en pantalla',
  STATIC: 'QR impreso en el mostrador',
};

export type QrChargeStatus = 'PENDING' | 'PAID' | 'PAID_UNAPPLIED' | 'CANCELLED' | 'EXPIRED' | 'FAILED';

export interface LocationQrStatus {
  locationId: string;
  locationName: string;
  qrMode: QrMode;
  configured: boolean;
  qrImageUrl: string | null;
}

/** GET /mercadopago/account — nunca trae el token. */
export interface MercadoPagoStatus {
  connected: boolean;
  nickname: string | null;
  mpUserId: string | null;
  testMode: boolean;
  connectedAt: string | null;
  locations: LocationQrStatus[];
}

export interface ConfigureLocationQrInput {
  qrMode?: QrMode;
  streetName?: string;
  streetNumber?: string;
  cityName?: string;
  stateName?: string;
  latitude?: number;
  longitude?: number;
  reference?: string;
}

/** GET /mercadopago/availability — para la ventana de cobro. */
export interface QrAvailability {
  available: boolean;
  mode: QrMode;
  testMode: boolean;
  reason: string | null;
}

export interface QrChargeResponse {
  id: string;
  orderId: string;
  locationId: string;
  mode: QrMode;
  amount: number;
  status: QrChargeStatus;
  /** Texto del QR a dibujar (modo DYNAMIC, solo mientras espera). */
  qrData: string | null;
  /** Imagen del QR fijo del local (modo STATIC, solo mientras espera). */
  qrImageUrl: string | null;
  statusMessage: string | null;
  expiresAt: string;
  createdAt: string;
  resolvedAt: string | null;
  order: OrderResponse;
}
