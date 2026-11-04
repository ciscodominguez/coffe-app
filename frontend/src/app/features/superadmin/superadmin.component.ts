import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { USER_ROLE_LABELS, UserRole } from '../../core/models/auth.model';
import { AuthService } from '../../core/services/auth.service';
import { extractErrorMessage } from '../../core/services/error.util';
import { SupportRequestItem, browserSummary } from '../../core/services/support.service';
import { ThemeService } from '../../core/services/theme.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { ModalComponent } from '../../shared/components/modal/modal.component';
import {
  BILLING_LABELS,
  BusinessDetail,
  BusinessPlan,
  BusinessStatus,
  BusinessSummary,
  FISCAL_LABELS,
  PLAN_LABELS,
  PlatformService,
  PlatformSettings,
  STATUS_LABELS,
  ServiceRequestItem,
  UpdateBusinessInput,
} from './platform.service';

type Filter = 'ALL' | BusinessStatus;

/**
 * Panel de superadmin (paso 3.1): Fran ve todos los cafés clientes, su
 * actividad, y los activa / suspende / da de baja o les cambia el plan.
 * Pantalla aparte, fuera del menú de un café (el superadmin no tiene café).
 */
@Component({
    selector: 'app-superadmin',
    imports: [DatePipe, DecimalPipe, FormsModule, IconComponent, ModalComponent],
    templateUrl: './superadmin.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './superadmin.component.css'
})
export class SuperadminComponent {
  private readonly platform = inject(PlatformService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  readonly theme = inject(ThemeService);

  readonly statusLabels = STATUS_LABELS;
  readonly planLabels = PLAN_LABELS;
  readonly statuses: BusinessStatus[] = ['TRIAL', 'ACTIVE', 'SUSPENDED', 'CANCELLED'];
  readonly plans: BusinessPlan[] = ['TRIAL', 'BASIC', 'PRO', 'ENTERPRISE'];

  readonly billingLabels = BILLING_LABELS;

  readonly adminEmail = computed(() => this.auth.user()?.email ?? '');

  // --- V16: configuración del cobro ---
  readonly settings = signal<PlatformSettings | null>(null);
  readonly settingsOpen = signal(false);
  readonly settingsSaving = signal(false);
  readonly settingsError = signal<string | null>(null);
  readonly editPrice = signal<number | null>(null);
  readonly editTrialDays = signal(14);
  readonly editGraceDays = signal(7);
  readonly editWhatsapp = signal('');

  // --- V18: pedidos de ayuda (botón de soporte) ---
  readonly helpRequests = signal<SupportRequestItem[]>([]);
  readonly helpOpen = signal(false);
  readonly pendingHelp = computed(() => this.helpRequests().filter((r) => r.status === 'PENDING').length);
  readonly helpBusy = signal<string | null>(null);
  readonly browser = (ua: string) => browserSummary(ua, false);

  // --- V17: pedidos de arrepentimiento / baja ---
  readonly requests = signal<ServiceRequestItem[]>([]);
  readonly requestsOpen = signal(false);
  readonly pendingRequests = computed(() => this.requests().filter((r) => r.status === 'PENDING').length);
  readonly requestBusy = signal<string | null>(null);

  // --- V16: cobro del café abierto ---
  readonly editExempt = signal(false);
  readonly extendDays = signal(7);
  readonly extending = signal(false);
  readonly businesses = signal<BusinessSummary[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly search = signal('');
  readonly filter = signal<Filter>('ALL');

  readonly counts = computed(() => {
    const list = this.businesses();
    return {
      ALL: list.length,
      TRIAL: list.filter((b) => b.status === 'TRIAL').length,
      ACTIVE: list.filter((b) => b.status === 'ACTIVE').length,
      SUSPENDED: list.filter((b) => b.status === 'SUSPENDED').length,
      CANCELLED: list.filter((b) => b.status === 'CANCELLED').length,
    } as Record<Filter, number>;
  });

  readonly filtered = computed(() => {
    const q = normalize(this.search());
    const f = this.filter();
    return this.businesses().filter((b) => {
      if (f !== 'ALL' && b.status !== f) return false;
      if (!q) return true;
      return [b.legalName, b.tradeName, b.taxId, b.ownerName, b.ownerEmail].some((v) => v && normalize(v).includes(q));
    });
  });

  // --- Detalle ---
  readonly detail = signal<BusinessDetail | null>(null);
  readonly detailLoading = signal(false);
  readonly saving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly editStatus = signal<BusinessStatus>('TRIAL');
  readonly editReason = signal('');
  readonly editPlan = signal<BusinessPlan>('TRIAL');
  readonly editNotes = signal('');

  readonly blocking = computed(() => {
    const d = this.detail();
    const s = this.editStatus();
    return !!d && s !== d.summary.status && (s === 'SUSPENDED' || s === 'CANCELLED');
  });
  readonly unblocking = computed(() => {
    const d = this.detail();
    const s = this.editStatus();
    return !!d && isBlocked(d.summary.status) && !isBlocked(s);
  });
  readonly dirty = computed(() => {
    const d = this.detail();
    if (!d) return false;
    return (
      this.editStatus() !== d.summary.status ||
      this.editPlan() !== d.summary.plan ||
      this.editNotes().trim() !== (d.adminNotes ?? '') ||
      this.editExempt() !== d.summary.billingExempt ||
      (isBlocked(this.editStatus()) && this.editReason().trim() !== (d.summary.statusReason ?? ''))
    );
  });

  constructor() {
    void this.load();
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.errorMessage.set(null);
    try {
      const [list, settings, requests, help] = await Promise.all([
        this.platform.businesses(),
        this.platform.settings(),
        this.platform.requests(),
        this.platform.supportRequests(),
      ]);
      this.businesses.set(list);
      this.settings.set(settings);
      this.requests.set(requests);
      this.helpRequests.set(help);
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudo cargar la lista de cafés.'));
    } finally {
      this.loading.set(false);
    }
  }

  async open(business: BusinessSummary): Promise<void> {
    this.formError.set(null);
    this.detailLoading.set(true);
    try {
      this.fill(await this.platform.business(business.id));
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo abrir el café.'));
    } finally {
      this.detailLoading.set(false);
    }
  }

  close(): void {
    this.detail.set(null);
  }

  async save(): Promise<void> {
    const d = this.detail();
    if (!d || this.saving()) return;
    const status = this.editStatus();
    if (this.blocking() && !this.editReason().trim()) {
      this.formError.set('Escribí el motivo: es lo que va a ver el café cuando quiera entrar.');
      return;
    }
    const input: UpdateBusinessInput = {};
    if (status !== d.summary.status) input.status = status;
    if (isBlocked(status)) input.statusReason = this.editReason().trim();
    if (this.editPlan() !== d.summary.plan) input.plan = this.editPlan();
    if (this.editNotes().trim() !== (d.adminNotes ?? '')) input.adminNotes = this.editNotes().trim();
    if (this.editExempt() !== d.summary.billingExempt) input.billingExempt = this.editExempt();

    this.saving.set(true);
    this.formError.set(null);
    try {
      const updated = await this.platform.update(d.summary.id, input);
      this.fill(updated);
      this.businesses.update((list) => list.map((b) => (b.id === updated.summary.id ? updated.summary : b)));
      this.toast.success(
        input.status ? `${displayName(updated.summary)}: ${STATUS_LABELS[updated.summary.status].toLowerCase()}.` : 'Cambios guardados.',
      );
    } catch (error) {
      this.formError.set(extractErrorMessage(error, 'No se pudieron guardar los cambios.'));
    } finally {
      this.saving.set(false);
    }
  }

  /** Suma días de prueba (desde hoy si ya estaba vencido); si estaba suspendido por falta de pago, lo reactiva. */
  async extendTrial(): Promise<void> {
    const d = this.detail();
    const days = Math.round(Number(this.extendDays()));
    if (!d || this.extending()) return;
    if (!days || days < 1 || days > 90) {
      this.formError.set('Sumá entre 1 y 90 días.');
      return;
    }
    this.extending.set(true);
    this.formError.set(null);
    try {
      const updated = await this.platform.update(d.summary.id, { extendTrialDays: days });
      this.fill(updated);
      this.businesses.update((list) => list.map((b) => (b.id === updated.summary.id ? updated.summary : b)));
      this.toast.success(`Listo: ${days} días más de prueba.`);
    } catch (error) {
      this.formError.set(extractErrorMessage(error, 'No se pudo extender la prueba.'));
    } finally {
      this.extending.set(false);
    }
  }

  async toggleRequest(r: ServiceRequestItem): Promise<void> {
    if (this.requestBusy()) return;
    this.requestBusy.set(r.id);
    try {
      const updated = await this.platform.updateRequest(r.id, { status: r.status === 'DONE' ? 'PENDING' : 'DONE' });
      this.requests.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo actualizar el pedido.'));
    } finally {
      this.requestBusy.set(null);
    }
  }

  async saveRequestNote(r: ServiceRequestItem, note: string): Promise<void> {
    if ((r.adminNote ?? '') === note.trim()) return;
    try {
      const updated = await this.platform.updateRequest(r.id, { adminNote: note });
      this.requests.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
      this.toast.success('Nota guardada.');
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo guardar la nota.'));
    }
  }

  async toggleHelp(r: SupportRequestItem): Promise<void> {
    if (this.helpBusy()) return;
    this.helpBusy.set(r.id);
    try {
      const updated = await this.platform.updateSupportRequest(r.id, { status: r.status === 'DONE' ? 'PENDING' : 'DONE' });
      this.helpRequests.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo actualizar el pedido de ayuda.'));
    } finally {
      this.helpBusy.set(null);
    }
  }

  async saveHelpNote(r: SupportRequestItem, note: string): Promise<void> {
    if ((r.adminNote ?? '') === note.trim()) return;
    try {
      const updated = await this.platform.updateSupportRequest(r.id, { adminNote: note });
      this.helpRequests.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
      this.toast.success('Nota guardada.');
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo guardar la nota.'));
    }
  }

  roleName(role: string | null): string {
    return role ? (USER_ROLE_LABELS[role as UserRole] ?? role) : '';
  }

  openSettings(): void {
    const s = this.settings();
    this.editPrice.set(s?.monthlyPrice ?? null);
    this.editTrialDays.set(s?.trialDays ?? 14);
    this.editGraceDays.set(s?.graceDays ?? 7);
    this.editWhatsapp.set(s?.supportWhatsapp ?? '');
    this.settingsError.set(null);
    this.settingsOpen.set(true);
  }

  async saveSettings(): Promise<void> {
    const price = this.editPrice() === null || (this.editPrice() as unknown) === '' ? null : Number(this.editPrice());
    if (price !== null && !(price > 0)) {
      this.settingsError.set('El precio tiene que ser mayor a cero (o dejalo vacío si todavía no cobrás).');
      return;
    }
    const whatsapp = this.editWhatsapp().replace(/\D/g, '');
    if (whatsapp && (whatsapp.length < 8 || whatsapp.length > 15)) {
      this.settingsError.set('Revisá el WhatsApp: con código de país y de área, sin el 0 ni el 15. Ej: 5492235550000.');
      return;
    }
    this.settingsSaving.set(true);
    this.settingsError.set(null);
    try {
      this.settings.set(
        await this.platform.saveSettings({
          monthlyPrice: price,
          trialDays: Number(this.editTrialDays()),
          graceDays: Number(this.editGraceDays()),
          supportWhatsapp: whatsapp || null,
        }),
      );
      this.settingsOpen.set(false);
      this.toast.success('Configuración guardada.');
    } catch (error) {
      this.settingsError.set(extractErrorMessage(error, 'No se pudo guardar.'));
    } finally {
      this.settingsSaving.set(false);
    }
  }

  /** Texto corto del cobro de un café para la lista. */
  billingText(b: BusinessSummary): string {
    const date = (iso: string | null) => (iso ? new Date(iso).toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit' }) : '');
    switch (b.billingState) {
      case 'TRIAL':
        return `Prueba hasta ${date(b.accessUntil)}`;
      case 'ACTIVE':
        return `Pago hasta ${date(b.accessUntil)}`;
      case 'PAST_DUE':
        return `Se suspende el ${date(b.suspendsAt)}`;
      case 'SUSPENDED':
        return 'Suspendido por falta de pago';
      case 'EXEMPT':
        return 'Sin cobro';
      default:
        return '—';
    }
  }

  subscriptionText(status: string | null): string {
    switch (status) {
      case 'authorized':
        return 'Débito automático activo';
      case 'pending':
        return 'Empezó a suscribirse (sin confirmar)';
      case 'paused':
        return 'Débito en pausa';
      case 'cancelled':
        return 'Canceló el débito';
      default:
        return 'Sin suscribirse';
    }
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }

  // --- Ayudas para la vista ---

  name(b: BusinessSummary): string {
    return displayName(b);
  }

  /** 30712345679 → 30-71234567-9 */
  cuit(value: string): string {
    const digits = (value ?? '').replace(/\D/g, '');
    return digits.length === 11 ? `${digits.slice(0, 2)}-${digits.slice(2, 10)}-${digits.slice(10)}` : value;
  }

  fiscal(value: string): string {
    return FISCAL_LABELS[value] ?? value;
  }

  roleLabel(role: string): string {
    return USER_ROLE_LABELS[role as UserRole] ?? role;
  }

  isBlocked(status: BusinessStatus): boolean {
    return isBlocked(status);
  }

  /** "hoy", "ayer", "hace 5 días", "hace 3 meses" — para ver de un vistazo quién dejó de usarla. */
  ago(iso: string | null): string {
    if (!iso) return 'Nunca';
    const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000);
    if (days <= 0) return 'Hoy';
    if (days === 1) return 'Ayer';
    if (days < 30) return `Hace ${days} días`;
    const months = Math.floor(days / 30);
    return months === 1 ? 'Hace 1 mes' : `Hace ${months} meses`;
  }

  /** Más de 2 semanas sin pedidos: vale la pena llamar. */
  isQuiet(b: BusinessSummary): boolean {
    if (isBlocked(b.status)) return false;
    const ref = b.lastOrderAt ?? b.createdAt;
    return Date.now() - new Date(ref).getTime() > 14 * 86_400_000;
  }

  historyIcon(action: string): 'refresh' | 'tag' | 'note' | 'card' {
    return action === 'STATUS' ? 'refresh' : action === 'PLAN' ? 'tag' : action === 'BILLING' ? 'card' : 'note';
  }

  private fill(detail: BusinessDetail): void {
    this.detail.set(detail);
    this.editStatus.set(detail.summary.status);
    this.editReason.set(detail.summary.statusReason ?? '');
    this.editPlan.set(detail.summary.plan);
    this.editNotes.set(detail.adminNotes ?? '');
    this.editExempt.set(detail.summary.billingExempt);
  }
}

function isBlocked(status: BusinessStatus): boolean {
  return status === 'SUSPENDED' || status === 'CANCELLED';
}

function displayName(b: BusinessSummary): string {
  return b.tradeName || b.legalName;
}

function normalize(value: string): string {
  return value
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[-\s]/g, '');
}
