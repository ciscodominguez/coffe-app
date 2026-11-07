import { Injectable, inject, signal } from '@angular/core';
import { ApiService } from './api.service';

/**
 * En qué anda el cobro del café (V16):
 * TRIAL (prueba gratis) · ACTIVE (al día) · PAST_DUE (vencido, en los días de gracia) ·
 * SUSPENDED (suspendido por falta de pago) · EXEMPT (sin cobro) · BLOCKED (suspendido a mano).
 */
export type BillingState = 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'SUSPENDED' | 'EXEMPT' | 'BLOCKED';

export interface SubscriptionPayment {
  chargedAt: string;
  amount: number;
  status: 'approved' | 'rejected';
  statusDetail: string | null;
  coversUntil: string | null;
}

export interface SubscriptionStatus {
  state: BillingState;
  trialEndsAt: string | null;
  paidUntil: string | null;
  accessUntil: string | null;
  suspendsAt: string | null;
  daysLeft: number;
  monthlyPrice: number | null;
  graceDays: number;
  /** Lo que dice Mercado Pago: pending (falta confirmar), authorized, paused, cancelled. */
  subscriptionStatus: 'pending' | 'authorized' | 'paused' | 'cancelled' | null;
  payerEmail: string | null;
  canSubscribe: boolean;
  unavailableReason: string | null;
  billingExempt: boolean;
  payments: SubscriptionPayment[];
}

/** Suscripción del café a COFFE_APP (V16) — `/subscription` del backend. */
@Injectable({ providedIn: 'root' })
export class SubscriptionService {
  private readonly api = inject(ApiService);

  /** Último estado conocido (lo usa el menú para el aviso de arriba). */
  readonly status = signal<SubscriptionStatus | null>(null);

  async load(): Promise<SubscriptionStatus> {
    const status = await this.api.get<SubscriptionStatus>('/subscription');
    this.status.set(status);
    return status;
  }

  /** Pregunta a Mercado Pago cómo quedó (al volver de su página). */
  async refresh(): Promise<SubscriptionStatus> {
    const status = await this.api.post<SubscriptionStatus>('/subscription/refresh');
    this.status.set(status);
    return status;
  }

  /** Devuelve la página de Mercado Pago donde el dueño confirma la suscripción. */
  async checkout(payerEmail: string): Promise<string> {
    const res = await this.api.post<{ checkoutUrl: string }>('/subscription/checkout', { payerEmail });
    return res.checkoutUrl;
  }

  async cancel(): Promise<SubscriptionStatus> {
    const status = await this.api.post<SubscriptionStatus>('/subscription/cancel');
    this.status.set(status);
    return status;
  }
}
