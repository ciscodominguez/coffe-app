/**
 * Apps de delivery de cada local (V19). Espejo de {@code channel/dto} del
 * backend. Ver docs/INTEGRACION-APPS-DELIVERY.md.
 */
export type SalesChannelKind = 'PEDIDOSYA' | 'RAPPI' | 'UBER_EATS' | 'OTHER';
export type SalesChannelMode = 'OFF' | 'MANUAL' | 'AUTO';

export interface SalesChannel {
  id: string;
  locationId: string;
  kind: SalesChannelKind;
  name: string;
  mode: SalesChannelMode;
  commissionPct: number;
}

export interface SaveSalesChannelInput {
  locationId?: string;
  kind?: SalesChannelKind;
  name?: string;
  mode?: SalesChannelMode;
  commissionPct?: number;
}

/** Las apps conocidas, en el orden en que se muestran. "Otra app" se agrega aparte. */
export const KNOWN_CHANNELS: ReadonlyArray<{ kind: Exclude<SalesChannelKind, 'OTHER'>; name: string; hint: string }> = [
  { kind: 'PEDIDOSYA', name: 'PedidosYa', hint: 'La más usada en Argentina.' },
  { kind: 'RAPPI', name: 'Rappi', hint: '' },
  { kind: 'UBER_EATS', name: 'Uber Eats', hint: 'Volvió a Argentina en 2026.' },
];

/** Clase CSS del color de cada app (solo un color para reconocerla de un vistazo, sin logos). */
export function channelTone(kind: SalesChannelKind | null | undefined): string {
  switch (kind) {
    case 'PEDIDOSYA':
      return 'ch-pya';
    case 'RAPPI':
      return 'ch-rappi';
    case 'UBER_EATS':
      return 'ch-uber';
    default:
      return 'ch-other';
  }
}

export function isOn(channel: SalesChannel): boolean {
  return channel.mode !== 'OFF';
}
