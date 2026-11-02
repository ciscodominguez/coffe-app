import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BusinessResponse, LocationResponse } from '../../core/models/business.model';
import { UserRole } from '../../core/models/auth.model';
import { PAYMENT_METHOD_LABELS, PaymentMethod } from '../../core/models/order.model';
import { DASHBOARD_PERIOD_LABELS, DashboardPeriod, DashboardSummaryResponse } from '../../core/models/dashboard.model';
import { AuthService } from '../../core/services/auth.service';
import { BusinessService, LocationService } from '../../core/services/business.service';
import { CashSessionService } from '../../core/services/cash-session.service';
import { CategoryService, ProductService } from '../../core/services/catalog.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { extractErrorMessage } from '../../core/services/error.util';
import { TableService } from '../../core/services/table.service';
import { UserService } from '../../core/services/user.service';
import { IconComponent, IconName } from '../../shared/components/icon/icon.component';

/** Mismo grupo de roles que MANAGEMENT_ROLES en shell.component.ts. */
const MANAGEMENT_ROLES: readonly UserRole[] = ['OWNER', 'ADMIN', 'SUPERVISOR'];

const PERIODS: readonly DashboardPeriod[] = ['today', 'week', 'month'];

const SETUP_DISMISSED_KEY = 'cofe_app_setup_dismissed_';

interface QuickAction {
  title: string;
  text: string;
  icon: IconName;
  link: string;
  query?: Record<string, string>;
  primary?: boolean;
}

interface SetupStep {
  title: string;
  text: string;
  link: string;
  icon: IconName;
  done: boolean;
}

const QUICK_ACTIONS: Readonly<Record<UserRole, QuickAction[]>> = {
  WAITER: [
    { title: 'Tomar un pedido', text: 'Mesa o para llevar, en 3 pasos.', icon: 'plus', link: '/pedidos', primary: true },
    { title: 'Cobrar', text: 'Pedidos abiertos, cobro y ticket.', icon: 'wallet', link: '/pedidos', query: { tab: 'open' } },
    { title: 'Cocina', text: 'Ver cómo van los pedidos.', icon: 'flame', link: '/kds' },
    { title: 'Caja', text: 'Abrir, movimientos y cierre.', icon: 'lock', link: '/caja' },
  ],
  KITCHEN: [
    { title: 'Ver pedidos', text: 'Los pedidos para preparar, en vivo.', icon: 'flame', link: '/kds', primary: true },
    { title: 'Productos', text: 'Buscar un producto y sus ingredientes.', icon: 'coffee', link: '/productos' },
    { title: 'Stock', text: 'Qué insumos se están por acabar.', icon: 'box', link: '/insumos' },
  ],
  SUPERVISOR: [],
  ADMIN: [],
  OWNER: [],
};

const MANAGEMENT_ACTIONS: QuickAction[] = [
  { title: 'Tomar un pedido', text: 'Mesa o para llevar.', icon: 'plus', link: '/pedidos', primary: true },
  { title: 'Cobrar', text: 'Pedidos abiertos.', icon: 'wallet', link: '/pedidos', query: { tab: 'open' } },
  { title: 'Cocina', text: 'Pedidos en curso.', icon: 'flame', link: '/kds' },
  { title: 'Caja', text: 'Turno y cierre.', icon: 'lock', link: '/caja' },
];

/**
 * Inicio. Para cada rol, accesos rápidos a lo que más usa. Para gerencia,
 * además:
 *  - "Primeros pasos": qué falta cargar para que el equipo pueda trabajar
 *    (categorías, productos, mesas, equipo, primera caja), con un botón a
 *    cada lugar. Desaparece sola cuando está todo, o se puede ocultar.
 *  - Indicadores del local (Bloque E): ventas, pedidos, ticket promedio,
 *    más vendidos, métodos de pago y stock bajo, por período.
 * Los indicadores los calcula el backend (solo gerencia puede pedirlos).
 */
@Component({
    selector: 'app-dashboard',
    imports: [CommonModule, FormsModule, RouterLink, IconComponent],
    templateUrl: './dashboard.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly businessService = inject(BusinessService);
  private readonly locationService = inject(LocationService);
  private readonly dashboardService = inject(DashboardService);
  private readonly categoryService = inject(CategoryService);
  private readonly productService = inject(ProductService);
  private readonly tableService = inject(TableService);
  private readonly userService = inject(UserService);
  private readonly cashSessionService = inject(CashSessionService);

  readonly user = this.authService.user;
  readonly business = signal<BusinessResponse | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  readonly role = computed<UserRole | null>(() => (this.user()?.role as UserRole | undefined) ?? null);
  readonly isManagement = computed(() => {
    const role = this.role();
    return !!role && MANAGEMENT_ROLES.includes(role);
  });

  readonly greeting = computed(() => {
    const hour = new Date().getHours();
    const base = hour < 12 ? 'Buen día' : hour < 20 ? 'Buenas tardes' : 'Buenas noches';
    const name = this.user()?.firstName;
    return name ? `${base}, ${name}` : base;
  });

  readonly today = new Date();

  readonly quickActions = computed<QuickAction[]>(() => {
    const role = this.role();
    if (!role) return [];
    return this.isManagement() ? MANAGEMENT_ACTIONS : QUICK_ACTIONS[role];
  });

  // ---- Primeros pasos ----
  readonly setupSteps = signal<SetupStep[]>([]);
  readonly setupDismissed = signal(false);
  readonly setupDone = computed(() => this.setupSteps().filter((step) => step.done).length);
  readonly showSetup = computed(
    () => !this.setupDismissed() && this.setupSteps().length > 0 && this.setupDone() < this.setupSteps().length,
  );

  // ---- Indicadores ----
  readonly periods = PERIODS;
  readonly periodLabels = DASHBOARD_PERIOD_LABELS;
  readonly paymentMethodLabels = PAYMENT_METHOD_LABELS;

  readonly locations = signal<LocationResponse[]>([]);
  readonly selectedLocationId = signal<string | null>(null);
  readonly period = signal<DashboardPeriod>('today');

  readonly summary = signal<DashboardSummaryResponse | null>(null);
  readonly loadingSummary = signal(false);
  readonly summaryError = signal<string | null>(null);

  readonly salesByMethodEntries = computed(() => {
    const summary = this.summary();
    if (!summary) return [] as [PaymentMethod, number][];
    return (Object.entries(summary.salesByMethod) as [PaymentMethod, number][]).sort((a, b) => b[1] - a[1]);
  });

  readonly maxTopQuantity = computed(() => Math.max(1, ...(this.summary()?.topProducts.map((p) => p.quantity) ?? [1])));

  readonly lowStockCount = computed(() => {
    const summary = this.summary();
    if (!summary) return 0;
    return summary.lowIngredients.length + summary.lowProducts.length;
  });

  async ngOnInit(): Promise<void> {
    if (!this.isManagement()) {
      this.loading.set(false);
      return;
    }
    try {
      const [business, locations] = await Promise.all([this.businessService.getMe(), this.locationService.findAll()]);
      this.business.set(business);
      this.locations.set(locations);
      this.setupDismissed.set(this.readDismissed(business.id));
      const locationId = this.user()?.locationId ?? locations[0]?.id ?? null;
      this.selectedLocationId.set(locationId);
      await Promise.all([this.reloadSummary(), locationId ? this.loadSetup(locationId) : Promise.resolve()]);
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudo cargar el negocio.'));
    } finally {
      this.loading.set(false);
    }
  }

  async onLocationChange(locationId: string): Promise<void> {
    this.selectedLocationId.set(locationId);
    await this.reloadSummary();
  }

  async onPeriodChange(period: DashboardPeriod): Promise<void> {
    this.period.set(period);
    await this.reloadSummary();
  }

  async reloadSummary(): Promise<void> {
    const locationId = this.selectedLocationId();
    if (!locationId) return;
    this.loadingSummary.set(true);
    this.summaryError.set(null);
    try {
      const { from, to } = this.periodRange(this.period());
      this.summary.set(await this.dashboardService.summary(locationId, from, to));
    } catch (error) {
      this.summaryError.set(extractErrorMessage(error, 'No se pudieron cargar los indicadores.'));
    } finally {
      this.loadingSummary.set(false);
    }
  }

  share(amount: number): number {
    const total = this.summary()?.totalSales ?? 0;
    return total > 0 ? Math.round((amount / total) * 100) : 0;
  }

  dismissSetup(): void {
    this.setupDismissed.set(true);
    const id = this.business()?.id;
    if (!id) return;
    try {
      localStorage.setItem(SETUP_DISMISSED_KEY + id, '1');
    } catch {
      // Sin localStorage: se oculta solo por esta vez.
    }
  }

  /**
   * Cada paso se marca hecho con datos reales (no con un "tilde" manual): si
   * ya hay categorías, productos, mesas, más de una persona en el equipo y
   * al menos una caja abierta alguna vez. Si algo falla, esa tarjeta no se
   * muestra — nunca frena el Inicio.
   */
  private async loadSetup(locationId: string): Promise<void> {
    try {
      const [categories, products, tables, users, sessions] = await Promise.all([
        this.categoryService.findAll(),
        this.productService.findAll(),
        this.tableService.findAllByLocation(locationId),
        this.userService.findAll(),
        this.cashSessionService.findAllByLocation(locationId),
      ]);
      this.setupSteps.set([
        {
          title: 'Armá las categorías de tu carta',
          text: 'Ej.: Cafetería, Pastelería, Bebidas frías.',
          link: '/categorias',
          icon: 'tag',
          done: categories.some((category) => category.isActive),
        },
        {
          title: 'Cargá tus productos con su precio',
          text: 'Con foto, así el equipo los encuentra rápido.',
          link: '/productos',
          icon: 'coffee',
          done: products.some((product) => product.isActive),
        },
        {
          title: 'Cargá las mesas del salón',
          text: 'Podés crear varias de una vez.',
          link: '/mesas',
          icon: 'table',
          done: tables.some((table) => table.isActive),
        },
        {
          title: 'Sumá a tu equipo',
          text: 'Cada persona con su usuario y su rol.',
          link: '/users',
          icon: 'users',
          done: users.filter((user) => user.isActive).length > 1,
        },
        {
          title: 'Abrí la caja por primera vez',
          text: 'Con eso ya se puede cobrar.',
          link: '/caja',
          icon: 'wallet',
          done: sessions.length > 0,
        },
      ]);
    } catch {
      this.setupSteps.set([]);
    }
  }

  private readDismissed(businessId: string): boolean {
    try {
      return localStorage.getItem(SETUP_DISMISSED_KEY + businessId) === '1';
    } catch {
      return false;
    }
  }

  /**
   * Rango real de un período, en hora LOCAL del navegador: "hoy" desde las
   * 00:00, "esta semana" desde el lunes, "este mes" desde el día 1; los
   * tres hasta ahora. Se pasa a ISO-8601 recién al llamar al backend.
   */
  private periodRange(period: DashboardPeriod): { from: Date; to: Date } {
    const to = new Date();
    const from = new Date(to);
    from.setHours(0, 0, 0, 0);
    if (period === 'week') {
      const weekday = from.getDay();
      const daysSinceMonday = weekday === 0 ? 6 : weekday - 1;
      from.setDate(from.getDate() - daysSinceMonday);
    } else if (period === 'month') {
      from.setDate(1);
    }
    return { from, to };
  }
}
