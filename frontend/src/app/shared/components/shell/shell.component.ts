import { Component, OnInit, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { USER_ROLE_LABELS, UserRole } from '../../../core/models/auth.model';
import { AuthService } from '../../../core/services/auth.service';
import { LayoutService } from '../../../core/services/layout.service';
import { ThemeService } from '../../../core/services/theme.service';
import { TourService } from '../../../core/services/tour.service';
import { OfflineOrderQueueService } from '../../../core/services/offline-order-queue.service';
import { SubscriptionService } from '../../../core/services/subscription.service';
import { LegalService, LegalStatus } from '../../../core/services/legal.service';
import { extractErrorMessage } from '../../../core/services/error.util';
import { ModalComponent } from '../modal/modal.component';
import { IconComponent, IconName } from '../icon/icon.component';
import { TourComponent } from '../tour/tour.component';
import { SupportSheetComponent } from '../support/support-sheet.component';

/**
 * Roles que ven los datos de gestión — mismo grupo que los `roleGuard` de
 * esas rutas en app.routes.ts (el menú decide qué MOSTRAR, la ruta decide
 * qué DEJAR ENTRAR, y el backend es quien realmente lo hace cumplir).
 */
const MANAGEMENT_ROLES: readonly UserRole[] = ['OWNER', 'ADMIN', 'SUPERVISOR'];
const OWNER_ADMIN: readonly UserRole[] = ['OWNER', 'ADMIN'];
const FRONT_OF_HOUSE: readonly UserRole[] = ['WAITER', 'SUPERVISOR', 'ADMIN', 'OWNER'];
const ALL: readonly UserRole[] = ['OWNER', 'ADMIN', 'SUPERVISOR', 'WAITER', 'KITCHEN'];

interface NavItem {
  path: string;
  label: string;
  icon: IconName;
  roles: readonly UserRole[];
  tour?: string;
  exact?: boolean;
}

interface NavSection {
  title: string;
  tour?: string;
  items: NavItem[];
}

const SECTIONS: NavSection[] = [
  {
    title: 'Día a día',
    items: [
      { path: '/', label: 'Inicio', icon: 'home', roles: ALL, tour: 'nav-inicio', exact: true },
      { path: '/pedidos', label: 'Pedidos', icon: 'receipt', roles: FRONT_OF_HOUSE, tour: 'nav-pedidos' },
      { path: '/kds', label: 'Cocina', icon: 'flame', roles: ALL, tour: 'nav-cocina' },
      { path: '/caja', label: 'Caja', icon: 'wallet', roles: FRONT_OF_HOUSE, tour: 'nav-caja' },
    ],
  },
  {
    title: 'Carta',
    tour: 'nav-catalogo',
    items: [
      { path: '/productos', label: 'Productos', icon: 'coffee', roles: ALL },
      { path: '/opciones', label: 'Opciones', icon: 'sliders', roles: OWNER_ADMIN },
      { path: '/descuentos', label: 'Descuentos', icon: 'percent', roles: OWNER_ADMIN },
      { path: '/categorias', label: 'Categorías', icon: 'tag', roles: MANAGEMENT_ROLES },
      { path: '/insumos', label: 'Insumos y stock', icon: 'box', roles: ALL },
      { path: '/mesas', label: 'Mesas', icon: 'table', roles: MANAGEMENT_ROLES },
    ],
  },
  {
    title: 'Administración',
    items: [
      { path: '/reportes', label: 'Reportes', icon: 'chart', roles: MANAGEMENT_ROLES },
      { path: '/users', label: 'Equipo', icon: 'users', roles: MANAGEMENT_ROLES },
      { path: '/locations', label: 'Locales', icon: 'store', roles: MANAGEMENT_ROLES },
      { path: '/business', label: 'Negocio', icon: 'building', roles: MANAGEMENT_ROLES },
      { path: '/cobros-qr', label: 'Cobros con QR', icon: 'qr', roles: OWNER_ADMIN },
      { path: '/apps-delivery', label: 'Apps de delivery', icon: 'bag', roles: OWNER_ADMIN },
      { path: '/suscripcion', label: 'Suscripción', icon: 'card', roles: OWNER_ADMIN },
    ],
  },
];

/** Los 4 accesos de la barra de abajo en el celular (el resto va en "Más"). */
const MOBILE_PRIMARY: Readonly<Record<UserRole, string[]>> = {
  OWNER: ['/', '/pedidos', '/kds', '/caja'],
  ADMIN: ['/', '/pedidos', '/kds', '/caja'],
  SUPERVISOR: ['/', '/pedidos', '/kds', '/caja'],
  WAITER: ['/', '/pedidos', '/kds', '/caja'],
  KITCHEN: ['/', '/kds', '/productos', '/insumos'],
};

/**
 * Cáscara de la app autenticada, adaptada a cada pantalla:
 *  - Compu (≥1100px): menú lateral completo, agrupado por secciones.
 *  - Tablet (768–1099px): menú lateral angosto, solo íconos con su nombre abajo.
 *  - Celular (<768px): barra arriba + barra de 4 accesos abajo (al alcance
 *    del pulgar) + "Más" con el resto.
 * Cada persona ve solo lo que su rol puede usar.
 */
@Component({
    selector: 'app-shell',
    imports: [RouterLink, RouterLinkActive, RouterOutlet, IconComponent, TourComponent, ModalComponent, SupportSheetComponent],
    templateUrl: './shell.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './shell.component.css'
})
export class ShellComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  readonly theme = inject(ThemeService);
  readonly tour = inject(TourService);
  readonly layout = inject(LayoutService);
  private readonly offlineQueue = inject(OfflineOrderQueueService);
  private readonly subscription = inject(SubscriptionService);
  private readonly legal = inject(LegalService);

  /** V17 — el dueño tiene que aceptar la versión vigente de los términos. */
  readonly termsToAccept = signal<LegalStatus | null>(null);
  readonly acceptingTerms = signal(false);
  readonly termsError = signal<string | null>(null);

  readonly user = this.authService.user;
  readonly moreOpen = signal(false);
  /** V18 — ventana del botón "Soporte" (la ven todos los roles, en todas las pantallas). */
  readonly supportOpen = signal(false);
  readonly pendingOrders = this.offlineQueue.pendingCount;

  readonly role = computed<UserRole | null>(() => (this.user()?.role as UserRole | undefined) ?? null);

  readonly sections = computed(() => {
    const role = this.role();
    if (!role) return [];
    return SECTIONS.map((section) => ({
      ...section,
      items: section.items.filter((item) => item.roles.includes(role)),
    })).filter((section) => section.items.length > 0);
  });

  private readonly allItems = computed(() => this.sections().flatMap((section) => section.items));

  readonly mobilePrimary = computed(() => {
    const role = this.role();
    if (!role) return [];
    const paths = MOBILE_PRIMARY[role];
    return paths
      .map((path) => this.allItems().find((item) => item.path === path))
      .filter((item): item is NavItem => !!item);
  });

  readonly mobileMore = computed(() => {
    const primary = new Set(MOBILE_PRIMARY[this.role() ?? 'WAITER']);
    return this.sections()
      .map((section) => ({ ...section, items: section.items.filter((item) => !primary.has(item.path)) }))
      .filter((section) => section.items.length > 0);
  });

  readonly isManagement = computed(() => {
    const role = this.role();
    return !!role && MANAGEMENT_ROLES.includes(role);
  });

  /**
   * V16 — aviso arriba de todo para dueño y administrador: la prueba se
   * termina en 3 días o menos (sin débito automático), está vencido (días
   * de gracia) o suspendido por falta de pago.
   */
  readonly billingNotice = computed<{ tone: 'info' | 'warning' | 'danger'; text: string } | null>(() => {
    const s = this.subscription.status();
    if (!s || this.currentUrl().startsWith('/suscripcion')) return null;
    const days = s.daysLeft === 1 ? '1 día' : `${s.daysLeft} días`;
    if (s.state === 'TRIAL' && s.daysLeft <= 3 && s.subscriptionStatus !== 'authorized') {
      return { tone: 'info', text: `Tu prueba gratis termina en ${days}. Suscribite para seguir usando COFFE_APP sin cortes.` };
    }
    if (s.state === 'PAST_DUE') {
      return { tone: 'warning', text: `No registramos el pago de la suscripción. Tenés ${days} para pagar; después el café se suspende.` };
    }
    if (s.state === 'SUSPENDED') return { tone: 'danger', text: 'El café está suspendido por falta de pago.' };
    return null;
  });

  private readonly currentUrl = signal(this.router.url);

  readonly initials = computed(() => {
    const u = this.user();
    if (!u) return '';
    return `${u.firstName?.[0] ?? ''}${u.lastName?.[0] ?? ''}`.toUpperCase();
  });

  constructor() {
    // Al navegar, se cierra la hoja "Más" del celular.
    this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => {
      this.moreOpen.set(false);
      this.currentUrl.set(this.router.url);
    });
  }

  ngOnInit(): void {
    const u = this.user();
    if (u) this.tour.startIfFirstTime(u.id, u.role as UserRole, u.firstName);
    // V17 — términos nuevos (o que nunca aceptó): se los mostramos al dueño.
    if (u?.role === 'OWNER') {
      this.legal
        .status()
        .then((st) => this.termsToAccept.set(st.mustAccept ? st : null))
        .catch(() => undefined);
    }
    // V16 — estado del cobro (solo dueño y administrador lo ven).
    if (u && (u.role === 'OWNER' || u.role === 'ADMIN')) {
      this.subscription
        .load()
        .then((s) => {
          if (s.state === 'SUSPENDED' && !this.router.url.startsWith('/suscripcion')) this.router.navigate(['/suscripcion']);
        })
        .catch(() => undefined);
    }
  }

  async acceptTerms(): Promise<void> {
    const st = this.termsToAccept();
    if (!st || this.acceptingTerms()) return;
    this.acceptingTerms.set(true);
    this.termsError.set(null);
    try {
      await this.legal.accept(st.currentVersion);
      this.termsToAccept.set(null);
    } catch (error) {
      this.termsError.set(extractErrorMessage(error, 'No se pudo guardar. Probá de nuevo.'));
    } finally {
      this.acceptingTerms.set(false);
    }
  }

  roleLabel(role: string): string {
    return USER_ROLE_LABELS[role as UserRole] ?? role;
  }

  startTour(): void {
    const u = this.user();
    if (!u) return;
    this.moreOpen.set(false);
    this.tour.start(u.id, u.role as UserRole, u.firstName);
  }

  openSupport(): void {
    this.moreOpen.set(false);
    this.supportOpen.set(true);
  }

  exitImmersive(): void {
    void this.layout.exitImmersive();
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
