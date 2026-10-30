import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { Subscription } from 'rxjs';
import { UserRole } from '../../core/models/auth.model';
import {
  ORDER_STATUS_LABELS,
  OrderResponse,
  OrderStatus,
  OrderItemResponse,
  allowedRolesForTransition,
  channelLabel,
  latestBatch,
  nextStatus,
} from '../../core/models/order.model';
import { channelTone } from '../../core/models/sales-channel.model';
import { tableDisplayName } from '../../core/models/table.model';
import { AuthService } from '../../core/services/auth.service';
import { extractErrorMessage } from '../../core/services/error.util';
import { LayoutService } from '../../core/services/layout.service';
import { LocationResolverService } from '../../core/services/location-resolver.service';
import { OrderService } from '../../core/services/order.service';
import { OrdersRealtimeService } from '../../core/services/orders-realtime.service';
import { PrintContextService } from '../../core/services/print-context.service';
import { TableService } from '../../core/services/table.service';
import { TicketPrintService } from '../../core/services/ticket-print.service';
import { buildCustomerTicket, buildKitchenTicket } from '../../core/services/ticket-templates';
import { ToastService } from '../../core/services/toast.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { IconComponent, IconName } from '../../shared/components/icon/icon.component';
import { PaymentDialogComponent } from '../../shared/components/payment-dialog/payment-dialog.component';

const POLL_INTERVAL_MS = 20_000;
const CLOCK_TICK_MS = 15_000;

/** Columnas del tablero — nunca DELIVERED/CANCELLED (mismo criterio que OPEN_STATUSES del backend). */
const BOARD_STATUSES: readonly OrderStatus[] = ['PENDING', 'IN_PREPARATION', 'READY'];

/** Quién puede cobrar — mismo grupo que FRONT_OF_HOUSE_ROLES del backend. KITCHEN nunca maneja dinero. */
const PAYMENT_ROLES: readonly UserRole[] = ['WAITER', 'SUPERVISOR', 'ADMIN', 'OWNER'];

/** Minutos de espera a partir de los cuales el pedido se pinta en amarillo y en rojo. */
const WARN_MINUTES = 10;
const LATE_MINUTES = 20;

const COLUMN_HINTS: Readonly<Record<string, string>> = {
  PENDING: 'Recién entraron',
  IN_PREPARATION: 'Se están haciendo',
  READY: 'Para llevar a la mesa',
};

/** Etiquetas cortas para las pestañas del celular (entran las tres en una fila). */
const SHORT_LABELS: Readonly<Record<string, string>> = {
  PENDING: 'Nuevos',
  IN_PREPARATION: 'Preparando',
  READY: 'Listos',
};

const ACTION_ICONS: Readonly<Record<string, IconName>> = {
  IN_PREPARATION: 'flame',
  READY: 'check',
  DELIVERED: 'arrow-right',
};

/**
 * Pantalla de Cocina / Barra. Los pedidos entran solos (WebSocket, con
 * polling de respaldo) y cada tarjeta tiene UN botón grande con el
 * siguiente paso: Empezar → Listo → Entregar. El tiempo de espera se pinta
 * de color para ver de un vistazo qué está demorado.
 *
 *  - En el celular, las columnas se vuelven pestañas.
 *  - "Pantalla completa" esconde el menú y agranda todo, para un TV o
 *    monitor fijo en la cocina.
 *  - Cobrar abre la misma ventana de cobro que Pedidos.
 */
@Component({
    selector: 'app-kds-board',
    imports: [CommonModule, IconComponent, EmptyStateComponent, PaymentDialogComponent],
    providers: [OrdersRealtimeService],
    templateUrl: './kds-board.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './kds-board.component.css'
})
export class KdsBoardComponent implements OnInit, OnDestroy {
  private readonly authService = inject(AuthService);
  private readonly locationResolver = inject(LocationResolverService);
  private readonly orderService = inject(OrderService);
  private readonly tableService = inject(TableService);
  private readonly realtime = inject(OrdersRealtimeService);
  private readonly printContext = inject(PrintContextService);
  private readonly ticketPrint = inject(TicketPrintService);
  private readonly toast = inject(ToastService);
  readonly layout = inject(LayoutService);

  private pollHandle: ReturnType<typeof setInterval> | null = null;
  private tickHandle: ReturnType<typeof setInterval> | null = null;
  private eventsSubscription: Subscription | null = null;
  private knownIds = new Set<string>();

  readonly statusLabels = ORDER_STATUS_LABELS;
  /** V19 — color de cada app de delivery. */
  readonly channelTone = channelTone;
  readonly columns = BOARD_STATUSES;
  readonly columnHints = COLUMN_HINTS;
  readonly shortLabels = SHORT_LABELS;
  readonly realtimeStatus = this.realtime.status;

  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly orders = signal<OrderResponse[]>([]);
  readonly tableLabels = signal<Record<string, string>>({});
  readonly now = signal(Date.now());
  readonly pendingActionId = signal<string | null>(null);
  readonly confirmingCancelId = signal<string | null>(null);
  readonly mobileColumn = signal<OrderStatus>('PENDING');
  /** Pedidos que llegaron hace instantes — se resaltan unos segundos. */
  readonly freshIds = signal<Set<string>>(new Set());

  readonly payingOrder = signal<OrderResponse | null>(null);

  private readonly role = computed<UserRole | null>(() => (this.authService.user()?.role as UserRole) ?? null);
  readonly canCollectPayment = computed(() => {
    const role = this.role();
    return !!role && PAYMENT_ROLES.includes(role);
  });

  readonly byStatus = computed(() => {
    const groups: Record<string, OrderResponse[]> = { PENDING: [], IN_PREPARATION: [], READY: [] };
    for (const order of this.orders()) groups[order.status]?.push(order);
    for (const key of Object.keys(groups)) groups[key].sort((a, b) => a.createdAt.localeCompare(b.createdAt));
    return groups;
  });

  readonly totalOpen = computed(() => this.orders().length);

  async ngOnInit(): Promise<void> {
    try {
      const locationId = await this.locationResolver.resolve();
      await Promise.all([this.loadTables(locationId), this.loadOrders(locationId)]);

      this.realtime.connect(locationId);
      this.eventsSubscription = this.realtime.events$.subscribe((event) => this.upsertOrder(event.data));

      this.pollHandle = setInterval(() => void this.loadOrders(locationId), POLL_INTERVAL_MS);
      this.tickHandle = setInterval(() => this.now.set(Date.now()), CLOCK_TICK_MS);
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudo cargar la pantalla de cocina.'));
    } finally {
      this.loading.set(false);
    }
  }

  ngOnDestroy(): void {
    if (this.pollHandle !== null) clearInterval(this.pollHandle);
    if (this.tickHandle !== null) clearInterval(this.tickHandle);
    this.eventsSubscription?.unsubscribe();
    if (this.layout.immersive()) void this.layout.exitImmersive();
  }

  ordersFor(status: OrderStatus): OrderResponse[] {
    return this.byStatus()[status] ?? [];
  }

  tableLabelFor(order: OrderResponse): string {
    const app = channelLabel(order);
    if (app) return app;
    if (!order.tableId) return 'Para llevar';
    return this.tableLabels()[order.tableId] ?? 'Mesa';
  }

  minutesWaiting(order: OrderResponse): number {
    return Math.max(0, Math.floor((this.now() - new Date(order.createdAt).getTime()) / 60000));
  }

  urgency(order: OrderResponse): 'ok' | 'warn' | 'late' {
    if (order.status === 'READY') return 'ok';
    const minutes = this.minutesWaiting(order);
    if (minutes >= LATE_MINUTES) return 'late';
    if (minutes >= WARN_MINUTES) return 'warn';
    return 'ok';
  }

  elapsedLabel(order: OrderResponse): string {
    const minutes = this.minutesWaiting(order);
    return minutes < 1 ? 'Recién' : `${minutes} min`;
  }

  /**
   * Pedidos a los que les agregaron cosas (tanda 2, 3…): lo último agregado
   * se marca "+ Agregado" y lo anterior se atenúa porque ya se preparó.
   * En "Listo" se ve todo normal (ya está todo hecho).
   */
  itemState(order: OrderResponse, item: OrderItemResponse): 'normal' | 'done' | 'added' {
    const round = latestBatch(order);
    if (round === 1 || order.status === 'READY') return 'normal';
    return (item.batch ?? 1) < round ? 'done' : 'added';
  }

  hasAdditions(order: OrderResponse): boolean {
    return latestBatch(order) > 1 && order.status !== 'READY';
  }

  canTransition(order: OrderResponse, to: OrderStatus): boolean {
    const role = this.role();
    if (!role) return false;
    return allowedRolesForTransition(order.status, to).includes(role);
  }

  advanceTarget(order: OrderResponse): OrderStatus | null {
    return nextStatus(order.status);
  }

  advanceLabel(to: OrderStatus): string {
    if (to === 'IN_PREPARATION') return 'Empezar';
    if (to === 'READY') return 'Marcar listo';
    return 'Entregar';
  }

  advanceIcon(to: OrderStatus): IconName {
    return ACTION_ICONS[to] ?? 'arrow-right';
  }

  async advance(order: OrderResponse): Promise<void> {
    const to = nextStatus(order.status);
    if (!to) return;
    if (to === 'DELIVERED' && !this.isFullyPaid(order)) {
      if (this.canCollectPayment()) this.payingOrder.set(order);
      return;
    }
    await this.changeStatus(order, to);
  }

  isFullyPaid(order: OrderResponse): boolean {
    return order.balanceDue <= 0.001;
  }

  startPayment(order: OrderResponse): void {
    this.payingOrder.set(order);
  }

  onPaymentUpdated(order: OrderResponse): void {
    this.upsertOrder(order);
  }

  requestCancel(order: OrderResponse): void {
    this.confirmingCancelId.set(order.id);
  }

  cancelCancelRequest(): void {
    this.confirmingCancelId.set(null);
  }

  async confirmCancel(order: OrderResponse): Promise<void> {
    this.confirmingCancelId.set(null);
    await this.changeStatus(order, 'CANCELLED');
  }

  async printKitchenTicket(order: OrderResponse): Promise<void> {
    await this.print(order, 'kitchen');
  }

  async printCustomerTicket(order: OrderResponse): Promise<void> {
    await this.print(order, 'customer');
  }

  toggleImmersive(): void {
    void (this.layout.immersive() ? this.layout.exitImmersive() : this.layout.enterImmersive());
  }

  private async print(order: OrderResponse, kind: 'kitchen' | 'customer'): Promise<void> {
    try {
      const context = await this.printContext.get(order.locationId);
      const label = this.tableLabelFor(order);
      this.ticketPrint.print(
        kind === 'kitchen' ? buildKitchenTicket(order, label, context) : buildCustomerTicket(order, label, context),
      );
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo imprimir el ticket.'));
    }
  }

  private async changeStatus(order: OrderResponse, status: OrderStatus): Promise<void> {
    if (this.pendingActionId()) return;
    this.pendingActionId.set(order.id);
    try {
      const updated = await this.orderService.updateStatus(order.id, { status });
      this.upsertOrder(updated);
      if (status === 'CANCELLED') this.toast.info(`${this.tableLabelFor(order)}: pedido cancelado.`);
      if (status === 'DELIVERED') this.toast.success(`${this.tableLabelFor(order)}: entregado.`);
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo cambiar el estado del pedido.'));
    } finally {
      this.pendingActionId.set(null);
    }
  }

  /** Usado por la respuesta directa de cada acción y por cada evento del WebSocket. */
  private upsertOrder(order: OrderResponse): void {
    if (order.status === 'DELIVERED' || order.status === 'CANCELLED') {
      this.orders.set(this.orders().filter((existing) => existing.id !== order.id));
      if (this.payingOrder()?.id === order.id && order.status === 'CANCELLED') this.payingOrder.set(null);
      return;
    }
    const current = this.orders();
    const index = current.findIndex((existing) => existing.id === order.id);
    if (index === -1) {
      this.orders.set([...current, order]);
      this.markFresh(order.id);
    } else {
      this.orders.set(current.map((existing) => (existing.id === order.id ? order : existing)));
    }
    if (this.payingOrder()?.id === order.id) this.payingOrder.set(order);
  }

  private markFresh(id: string): void {
    if (this.knownIds.has(id)) return;
    this.knownIds.add(id);
    this.freshIds.set(new Set([...this.freshIds(), id]));
    setTimeout(() => {
      const next = new Set(this.freshIds());
      next.delete(id);
      this.freshIds.set(next);
    }, 8000);
  }

  private async loadOrders(locationId: string): Promise<void> {
    try {
      const orders = await this.orderService.findActiveByLocation(locationId);
      const firstLoad = this.knownIds.size === 0 && this.orders().length === 0;
      for (const order of orders) {
        if (firstLoad) this.knownIds.add(order.id);
        else this.markFresh(order.id);
      }
      this.orders.set(orders);
    } catch (error) {
      if (this.loading()) throw error;
      // Si falla el polling de respaldo, se reintenta en la próxima vuelta.
    }
  }

  private async loadTables(locationId: string): Promise<void> {
    const tables = await this.tableService.findAllByLocation(locationId);
    const labels: Record<string, string> = {};
    for (const table of tables) labels[table.id] = tableDisplayName(table);
    this.tableLabels.set(labels);
  }
}
