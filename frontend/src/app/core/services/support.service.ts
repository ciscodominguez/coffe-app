import { Injectable, inject, signal } from '@angular/core';
import { USER_ROLE_LABELS, UserRole } from '../models/auth.model';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';
import { BusinessService } from './business.service';
import { DiagnosticsService } from './diagnostics.service';

/** Espejo de {@code support/dto/SupportInfoResponse.java}. */
export interface SupportInfo {
  whatsapp: string | null;
  contact: string | null;
}

/** Espejo de {@code support/dto/SupportRequestResponse.java} (lo ve el superadmin). */
export interface SupportRequestItem {
  id: string;
  businessId: string;
  businessName: string | null;
  userName: string | null;
  userRole: string | null;
  page: string | null;
  message: string | null;
  lastError: string | null;
  userAgent: string | null;
  status: 'PENDING' | 'DONE';
  adminNote: string | null;
  createdAt: string;
  handledAt: string | null;
}

/** Mismas claves que lee el aviso de emergencia de index.html. */
const INFO_KEY = 'coffe.supportInfo';
const BUSINESS_KEY = 'coffe.supportBusiness';

/**
 * V18 — botón de soporte. Arma el mensaje de WhatsApp con los datos que
 * Fran necesita (café, quién, pantalla, último error, navegador) y deja
 * además un "pedido de ayuda" en su panel.
 *
 * El número de WhatsApp y el nombre del café se guardan en el navegador:
 * si el servidor no contesta (justo cuando más se necesita), el botón
 * sigue funcionando con lo último que se supo.
 */
@Injectable({ providedIn: 'root' })
export class SupportService {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly businessService = inject(BusinessService);
  private readonly diagnostics = inject(DiagnosticsService);

  readonly info = signal<SupportInfo | null>(readJson<SupportInfo>(INFO_KEY));
  readonly businessName = signal<string | null>(null);

  /** Trae el WhatsApp de soporte (público) y, con sesión, el nombre del café. Nunca falla. */
  async refresh(): Promise<void> {
    try {
      const info = await this.api.get<SupportInfo>('/support/info');
      this.info.set(info);
      writeJson(INFO_KEY, info);
    } catch {
      /* queda lo guardado */
    }
    const user = this.auth.user();
    if (!user?.businessId) return;
    const cached = readJson<{ id: string; name: string }>(BUSINESS_KEY);
    if (cached?.id === user.businessId) this.businessName.set(cached.name);
    try {
      const { displayName } = await this.businessService.getDisplayName();
      this.businessName.set(displayName);
      writeJson(BUSINESS_KEY, { id: user.businessId, name: displayName });
    } catch {
      /* queda lo guardado */
    }
  }

  /** Texto del WhatsApp: todo lo que hace falta para ayudar sin tener que preguntar. */
  buildMessage(page: string, what: string): string {
    const user = this.auth.user();
    const error = this.diagnostics.recent();
    const lines = ['Hola, necesito ayuda con COFFE_APP.'];
    if (user) {
      lines.push(`Café: ${this.businessName() ?? '(no se pudo leer el nombre)'}`);
      const role = USER_ROLE_LABELS[user.role as UserRole] ?? user.role;
      lines.push(`Quién: ${`${user.firstName ?? ''} ${user.lastName ?? ''}`.trim() || user.email} (${role}) — ${user.email}`);
    }
    lines.push(`Pantalla: ${page}`);
    const hora = new Date().toLocaleString('es-AR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    });
    lines.push(`Hora: ${hora}`);
    if (what.trim()) lines.push(`Qué pasó: ${what.trim()}`);
    if (error) lines.push(`Último error: ${error.message}`);
    lines.push(`Internet: ${navigator.onLine ? 'conectado' : 'SIN CONEXIÓN'}`);
    lines.push(`Navegador: ${browserSummary()}`);
    return lines.join('\n');
  }

  whatsappUrl(message: string): string | null {
    const number = this.info()?.whatsapp?.replace(/\D/g, '');
    return number ? `https://wa.me/${number}?text=${encodeURIComponent(message)}` : null;
  }

  /** Deja el pedido de ayuda en el panel de Fran. */
  send(page: string, message: string): Promise<SupportRequestItem> {
    return this.api.post<SupportRequestItem>('/support/requests', {
      page,
      message: message.trim() || null,
      lastError: this.diagnostics.recent()?.message ?? null,
      userAgent: navigator.userAgent.slice(0, 255),
    });
  }
}

/** "Chrome 129 en Android" — corto, para que se entienda en el WhatsApp. */
export function browserSummary(ua = navigator.userAgent, withScreen = true): string {
  const browsers: [RegExp, string][] = [
    [/Edg\/(\d+)/, 'Edge'],
    [/SamsungBrowser\/(\d+)/, 'Samsung Internet'],
    [/Firefox\/(\d+)/, 'Firefox'],
    [/Chrome\/(\d+)/, 'Chrome'],
    [/Version\/(\d+).*Safari/, 'Safari'],
  ];
  const systems: [RegExp, string][] = [
    [/Android/, 'Android'],
    [/iPhone|iPad|iPod/, 'iPhone/iPad'],
    [/Windows/, 'Windows'],
    [/Mac OS X/, 'Mac'],
    [/Linux/, 'Linux'],
  ];
  const b = browsers.map(([re, name]) => [re.exec(ua), name] as const).find(([m]) => m);
  const os = systems.find(([re]) => re.test(ua))?.[1] ?? 'otro sistema';
  const text = `${b ? `${b[1]} ${b[0]![1]}` : 'otro navegador'} en ${os}`;
  return withScreen ? `${text}, pantalla ${window.innerWidth}x${window.innerHeight}` : text;
}

function readJson<T>(key: string): T | null {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

function writeJson(key: string, value: unknown): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    /* sin almacenamiento */
  }
}
