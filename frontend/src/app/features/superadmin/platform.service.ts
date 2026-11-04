import { Injectable, inject } from '@angular/core';
import { ApiService } from '../../core/services/api.service';
import { SubscriptionStatus } from '../../core/services/subscription.service';
import { SupportRequestItem } from '../../core/services/support.service';

/** Estado de un café cliente (mismo enum que BusinessStatus del backend). */
export type BusinessStatus = 'TRIAL' | 'ACTIVE' | 'SUSPENDED' | 'CANCELLED';
/** Plan (por ahora solo una etiqueta: no limita nada). */
export type BusinessPlan = 'TRIAL' | 'BASIC' | 'PRO' | 'ENTERPRISE';

export const STATUS_LABELS: Readonly<Record<BusinessStatus, string>> = {
  TRIAL: 'En prueba',
  ACTIVE: 'Activo',
  SUSPENDED: 'Suspendido',
  CANCELLED: 'Dado de baja',
};

export const PLAN_LABELS: Readonly<Record<BusinessPlan, string>> = {
  TRIAL: 'Prueba',
  BASIC: 'Básico',
  PRO: 'Pro',
  ENTERPRISE: 'Empresa',
};

export const FISCAL_LABELS: Readonly<Record<string, string>> = {
  MONOTRIBUTO: 'Monotributo',
  RESPONSABLE_INSCRIPTO: 'Responsable inscripto',
  EXENTO: 'Exento',
};

export interface BusinessSummary {
  id: string;
  legalName: string;
  tradeName: string | null;
  taxId: string;
  fiscalCondition: string;
  plan: BusinessPlan;
  status: BusinessStatus;
  statusReason: string | null;
  createdAt: string;
  ownerName: string | null;
  ownerEmail: string | null;
  locations: number;
  activeUsers: number;
  ordersLast30Days: number;
  lastOrderAt: string | null;
  // V16 — cobro
  billingState: BillingState;
  accessUntil: string | null;
  suspendsAt: string | null;
  billingExempt: boolean;
  subscriptionStatus: string | null;
}

/** Ver core/services/subscription.service.ts. */
export type BillingState = 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'SUSPENDED' | 'EXEMPT' | 'BLOCKED';

export const BILLING_LABELS: Readonly<Record<BillingState, string>> = {
  TRIAL: 'En prueba',
  ACTIVE: 'Al día',
  PAST_DUE: 'Sin pagar',
  SUSPENDED: 'Suspendido por pago',
  EXEMPT: 'Sin cobro',
  BLOCKED: '—',
};

export interface PlatformSettings {
  monthlyPrice: number | null;
  trialDays: number;
  graceDays: number;
  mercadoPagoConfigured?: boolean;
  /** V18 — WhatsApp del botón de soporte (solo números, con código de país: 5492235550000). */
  supportWhatsapp?: string | null;
}

export interface BusinessDetail {
  summary: BusinessSummary;
  adminNotes: string | null;
  users: { name: string; email: string; role: string; isActive: boolean }[];
  locations: { name: string; address: string | null; isActive: boolean }[];
  history: { action: 'STATUS' | 'PLAN' | 'NOTES' | 'BILLING'; detail: string; adminEmail: string | null; createdAt: string }[];
  billing: SubscriptionStatus;
}

/** V17 — pedido del "Botón de arrepentimiento" o del "Botón de baja". */
export interface ServiceRequestItem {
  id: string;
  code: string;
  type: 'WITHDRAWAL' | 'CANCELLATION';
  businessId: string | null;
  businessName: string | null;
  contactName: string;
  email: string;
  taxId: string | null;
  cafeName: string | null;
  message: string | null;
  status: 'PENDING' | 'DONE';
  result: string | null;
  adminNote: string | null;
  createdAt: string;
  handledAt: string | null;
}

export interface UpdateBusinessInput {
  status?: BusinessStatus;
  statusReason?: string;
  plan?: BusinessPlan;
  adminNotes?: string;
  billingExempt?: boolean;
  extendTrialDays?: number;
}

/** Panel de superadmin (V15) — `/platform/**` del backend. */
@Injectable({ providedIn: 'root' })
export class PlatformService {
  private readonly api = inject(ApiService);

  me(): Promise<{ email: string; name: string }> {
    return this.api.get('/platform/me');
  }

  businesses(): Promise<BusinessSummary[]> {
    return this.api.get('/platform/businesses');
  }

  business(id: string): Promise<BusinessDetail> {
    return this.api.get(`/platform/businesses/${id}`);
  }

  update(id: string, input: UpdateBusinessInput): Promise<BusinessDetail> {
    return this.api.patch(`/platform/businesses/${id}`, input);
  }

  requests(): Promise<ServiceRequestItem[]> {
    return this.api.get('/platform/requests');
  }

  updateRequest(id: string, input: { status?: 'PENDING' | 'DONE'; adminNote?: string }): Promise<ServiceRequestItem> {
    return this.api.patch(`/platform/requests/${id}`, input);
  }

  /** V18 — pedidos de ayuda del botón de soporte. */
  supportRequests(): Promise<SupportRequestItem[]> {
    return this.api.get('/platform/support');
  }

  updateSupportRequest(id: string, input: { status?: 'PENDING' | 'DONE'; adminNote?: string }): Promise<SupportRequestItem> {
    return this.api.patch(`/platform/support/${id}`, input);
  }

  settings(): Promise<PlatformSettings> {
    return this.api.get('/platform/settings');
  }

  saveSettings(input: PlatformSettings): Promise<PlatformSettings> {
    return this.api.put('/platform/settings', input);
  }
}
