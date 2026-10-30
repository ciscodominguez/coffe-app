import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Component, OnDestroy, OnInit, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { UserRole } from '../../core/models/auth.model';
import { CategoryResponse } from '../../core/models/category.model';
import {
  CreateOrderInput,
  ORDER_STATUS_LABELS,
  OrderResponse,
  OrderStatus,
  allowedRolesForTransition,
  channelLabel,
  itemOptionsLabel,
} from '../../core/models/order.model';
import { SalesChannel, channelTone } from '../../core/models/sales-channel.model';
import { SalesChannelService } from '../../core/services/sales-channel.service';
import { ModalComponent } from '../../shared/components/modal/modal.component';
import { OptionGroupResponse, availableOptions } from '../../core/models/option.model';
import { QueuedOrder } from '../../core/models/order-queue.model';
import { ProductResponse } from '../../core/models/product.model';
import { TableResponse, tableDisplayName } from '../../core/models/table.model';
import { AuthService } from '../../core/services/auth.service';
import { CategoryService, ProductService } from '../../core/services/catalog.service';
import { extractErrorMessage } from '../../core/services/error.util';
import { LocationResolverService } from '../../core/services/location-resolver.service';
import { OfflineOrderQueueService } from '../../core/services/offline-order-queue.service';
import { OptionGroupService } from '../../core/services/option-group.service';
import { OrderService } from '../../core/services/order.service';
import { OrdersRealtimeService } from '../../core/services/orders-realtime.service';
import { PrintContextService } from '../../core/services/print-context.service';
import { TableService } from '../../core/services/table.service';
import { FloorService } from '../../core/services/floor.service';
import { FloorArea, isPlacedIn } from '../../core/models/floor.model';
import { CanvasItem, CanvasTable, FloorCanvasComponent } from '../../shared/components/floor-canvas/floor-canvas.component';
import { TicketPrintService } from '../../core/services/ticket-print.service';
import { buildCustomerTicket, buildKitchenTicket } from '../../core/services/ticket-templates';
import { ToastService } from '../../core/services/toast.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { OptionPickerComponent, PickedOptions } from '../../shared/components/option-picker/option-picker.component';
import { PaymentDialogComponent } from '../../shared/components/payment-dialog/payment-dialog.component';

/**
 * Línea del carrito en construcción — todavía no es un pedido, así que
 * guarda precio/nombre para mostrarlos. Un mismo producto con opciones
 * distintas (un latte chico y uno grande) son dos líneas: `key` = producto
 * + opciones elegidas.
 */
interface CartLine {
  key: string;
  productId: string;
  productName: string;
  /** V11 — opciones elegidas (ids para el backend, nombres para mostrar). */
  optionIds: string[];
  optionLabels: string[];
  /** Precio del producto + extras de las opciones (el backend lo recalcula igual). */
  unitPrice: number;
  quantity: number;
  notes: string;
  showNotes: boolean;
}

function lineKey(productId: string, optionIds: string[]): string {
  return `${productId}|${[...optionIds].sort().join(',')}`;
}

/** Nunca viaja al backend: solo decide si se exige mesa. */
type DiningOption = 'DINE_IN' | 'TAKEAWAY' | 'APP';

/** Pasos del pedido nuevo: 1 dónde, 2 productos, 3 revisar, y la pantalla de "enviado". */
type Step = 1 | 2 | 3 | 'sent';

type Tab = 'new' | 'open';

const POLL_INTERVAL_MS = 20_000;
const CLOCK_TICK_MS = 30_000;
/** V18 — una mesa ocupada hace más de esto se marca en rojo en el plano. */
const LATE_TABLE_MINUTES = 45;
const TABLE_VIEW_KEY = 'coffe.waiterTablesView';
const money = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 0 });
const CLOSED_STATUSES: readonly OrderStatus[] = ['DELIVERED', 'CANCELLED'];

interface SentSummary {
  tableLabel: string;
  itemCount: number;
  total: number;
  online: boolean;
  /** true si se sumó a un pedido que ya estaba abierto (no es un pedido nuevo). */
  added: boolean;
}

/**
 * Pantalla de Pedidos, pensada para que cualquiera la use sin explicación:
 *
 * "Nuevo pedido", en 3 pasos guiados:
 *   1. ¿Dónde? — "Para llevar" o una mesa (las que ya tienen un pedido
 *      abierto se marcan, porque el backend no deja dos a la vez).
 *   2. Productos — tocar para sumar; categorías y buscador arriba.
 *   3. Revisar — cantidades, notas y "Enviar a cocina".
 *
 * "Pedidos abiertos": los pedidos en curso del local, con su estado en
 * vivo, y los botones para cobrar, imprimir el ticket y entregar — así la
 * persona que atiende no tiene que ir a la pantalla de Cocina para cobrar.
 *
 * Cada envío pasa PRIMERO por la cola offline (OfflineOrderQueueService),
 * igual que antes: si se corta internet, el pedido queda guardado en el
 * dispositivo y se envía solo al volver la conexión.
 */
@Component({
    selector: 'app-waiter-order',
    imports: [CommonModule, FormsModule, IconComponent, EmptyStateComponent, PaymentDialogComponent, OptionPickerComponent, FloorCanvasComponent, ModalComponent],
    providers: [OrdersRealtimeService],
    templateUrl: './waiter-order.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './waiter-order.component.css'
})
export class WaiterOrderComponent implements OnInit, OnDestroy {
  private readonly authService = inject(AuthService);
  private readonly locationResolver = inject(LocationResolverService);
  private readonly tableService = inject(TableService);
  private readonly categoryService = inject(CategoryService);
  private readonly productService = inject(ProductService);
  private readonly optionGroupService = inject(OptionGroupService);
  private readonly orderService = inject(OrderService);
  private readonly offlineQueue = inject(OfflineOrderQueueService);
  private readonly realtime = inject(OrdersRealtimeService);
  private readonly printContext = inject(PrintContextService);
  private readonly ticketPrint = inject(TicketPrintService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);

  private eventsSubscription: Subscription | null = null;
  private pollHandle: ReturnType<typeof setInterval> | null = null;
  private tickHandle: ReturnType<typeof setInterval> | null = null;
  private resolvedLocationId: string | null = null;

  readonly statusLabels = ORDER_STATUS_LABELS;
  readonly realtimeStatus = this.realtime.status;
  readonly queue = this.offlineQueue.queue;
  readonly queueIsOnline = this.offlineQueue.isOnline;

  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  readonly tab = signal<Tab>('new');
  readonly step = signal<Step>(1);
  readonly sent = signal<SentSummary | null>(null);
  readonly showQueueDetail = signal(false);
  readonly now = signal(Date.now());

  readonly tables = signal<TableResponse[]>([]);

  // ---- V19: pedidos de apps de delivery (solo si el local tiene alguna prendida) ----
  private readonly salesChannelService = inject(SalesChannelService);
  readonly channelTone = channelTone;
  readonly appChannels = signal<SalesChannel[]>([]);
  /** App elegida para el pedido nuevo, y la ventana con sus datos. */
  readonly appChannel = signal<SalesChannel | null>(null);
  readonly appFormOpen = signal(false);
  readonly appRef = signal('');
  readonly appCustomer = signal('');
  readonly appPickup = signal('');
  readonly appPaidByApp = signal(true);
  readonly appFormError = signal<string | null>(null);

  // ---- V18: plano del salón para elegir la mesa ----
  private readonly floorService = inject(FloorService);
  readonly floorAreas = signal<FloorArea[]>([]);
  private readonly floorItems = signal<CanvasItem[]>([]);
  private readonly floorItemAreas = signal<Record<string, string>>({});
  readonly floorAreaId = signal<string | null>(null);
  readonly tableView = signal<'plan' | 'list'>(readTableView());
  readonly lateMinutes = LATE_TABLE_MINUTES;

  readonly categories = signal<CategoryResponse[]>([]);
  readonly products = signal<ProductResponse[]>([]);
  readonly optionGroups = signal<OptionGroupResponse[]>([]);
  /** V11 — producto para el que está abierta la ventana de opciones. */
  readonly pickingProduct = signal<ProductResponse | null>(null);
  readonly openOrders = signal<OrderResponse[]>([]);

  readonly diningOption = signal<DiningOption | null>(null);
  readonly selectedTableId = signal<string | null>(null);
  readonly busyTableNotice = signal<TableResponse | null>(null);
  readonly selectedCategoryId = signal<string | null>(null);
  readonly searchTerm = signal('');
  readonly cart = signal<CartLine[]>([]);
  readonly orderNotes = signal('');
  readonly submitting = signal(false);
  /**
   * Hoja de ruta, paso 1: si no es null, el carrito se SUMA a este pedido
   * abierto ("la mesa pide otro cortado") en vez de crear uno nuevo.
   */
  readonly addingTo = signal<OrderResponse | null>(null);

  // ---- Cobro / acciones sobre pedidos abiertos ----
  readonly payingOrder = signal<OrderResponse | null>(null);
  readonly pendingActionId = signal<string | null>(null);

  private readonly role = computed<UserRole | null>(() => (this.authService.user()?.role as UserRole) ?? null);

  private readonly tablesById = computed<Record<string, TableResponse>>(() => {
    const map: Record<string, TableResponse> = {};
    for (const table of this.tables()) map[table.id] = table;
    return map;
  });

  /** Mesas con un pedido abierto ahora (el backend no deja cargar otro sobre esas). */
  readonly busyTableIds = computed(() => {
    const ids = new Set<string>();
    for (const order of this.openOrders()) if (order.tableId) ids.add(order.tableId);
    return ids;
  });

  /** Hay plano si hay al menos una zona con alguna mesa ubicada. */
  readonly hasPlan = computed(() => this.floorAreas().length > 0 && this.tables().some((t) => !!t.areaId && t.x !== null));
  readonly showPlan = computed(() => this.hasPlan() && this.tableView() === 'plan');

  /** Por mesa ocupada: hace cuántos minutos se sentaron (el pedido más viejo) y el total. */
  private readonly occupancy = computed(() => {
    const now = this.now();
    const map: Record<string, { minutes: number; total: number }> = {};
    for (const order of this.openOrders()) {
      if (!order.tableId) continue;
      const minutes = Math.max(0, Math.floor((now - new Date(order.createdAt).getTime()) / 60_000));
      const entry = (map[order.tableId] ??= { minutes: 0, total: 0 });
      entry.minutes = Math.max(entry.minutes, minutes);
      entry.total += order.total;
    }
    return map;
  });

  readonly planTables = computed<CanvasTable[]>(() => {
    const areaId = this.floorAreaId();
    if (!areaId) return [];
    const occupancy = this.occupancy();
    return this.tables()
      .filter((t) => isPlacedIn(t, areaId))
      .map((t) => {
        const busy = occupancy[t.id];
        const late = !!busy && busy.minutes >= LATE_TABLE_MINUTES;
        return {
          id: t.id,
          number: t.number,
          label: t.label,
          shape: t.shape,
          x: +t.x!,
          y: +t.y!,
          width: +t.width!,
          height: +t.height!,
          tone: busy ? (late ? 'late' : 'busy') : t.status === 'RESERVED' ? 'reserved' : 'free',
          lines: busy ? [busy.minutes + ' min', '$ ' + money.format(busy.total)] : ['Libre'],
          ariaLabel: busy
            ? `${tableDisplayName(t)}: ocupada hace ${busy.minutes} minutos, $ ${money.format(busy.total)}. Tocá para agregar productos.`
            : `${tableDisplayName(t)}: libre. Tocá para tomar el pedido.`,
        } satisfies CanvasTable;
      });
  });

  readonly planItems = computed(() => {
    const areaId = this.floorAreaId();
    const areas = this.floorItemAreas();
    return this.floorItems().filter((i) => areas[i.key] === areaId);
  });

  /** Mesas que no están en ninguna zona del plano: se muestran abajo para poder elegirlas igual. */
  readonly unplacedTables = computed(() => this.tables().filter((t) => !t.areaId || t.x === null));

  /** Cuántas mesas ocupadas hay en cada zona (para la pestaña). */
  readonly busyByArea = computed(() => {
    const busy = this.busyTableIds();
    const map: Record<string, number> = {};
    for (const t of this.tables()) if (t.areaId && busy.has(t.id)) map[t.areaId] = (map[t.areaId] ?? 0) + 1;
    return map;
  });

  readonly activeProducts = computed(() => this.products().filter((product) => product.isActive));

  /** V11 — grupos de opciones de cada producto (solo los que tienen algo disponible para elegir). */
  private readonly groupsByProduct = computed(() => {
    const map: Record<string, OptionGroupResponse[]> = {};
    for (const group of this.optionGroups()) {
      if (availableOptions(group).length === 0) continue;
      for (const productId of group.productIds) (map[productId] ??= []).push(group);
    }
    return map;
  });

  readonly pickingGroups = computed(() => {
    const product = this.pickingProduct();
    return product ? this.groupsOf(product.id) : [];
  });

  readonly filteredProducts = computed(() => {
    const categoryId = this.selectedCategoryId();
    const term = this.searchTerm().trim().toLowerCase();
    return this.activeProducts()
      .filter((product) => !categoryId || product.categoryId === categoryId)
      .filter(
        (product) =>
          !term || product.name.toLowerCase().includes(term) || (product.sku ?? '').toLowerCase().includes(term),
      )
      .sort((a, b) => a.name.localeCompare(b.name));
  });

  /** Solo categorías que tienen al menos un producto activo — no tiene sentido mostrar pestañas vacías. */
  readonly usedCategories = computed(() => {
    const used = new Set(this.activeProducts().map((product) => product.categoryId));
    return this.categories().filter((category) => used.has(category.id));
  });

  readonly cartTotal = computed(() => this.cart().reduce((sum, line) => sum + line.unitPrice * line.quantity, 0));
  readonly cartItemCount = computed(() => this.cart().reduce((sum, line) => sum + line.quantity, 0));
  private readonly cartQuantities = computed(() => {
    const map: Record<string, number> = {};
    for (const line of this.cart()) map[line.productId] = (map[line.productId] ?? 0) + line.quantity;
    return map;
  });

  readonly sortedOpenOrders = computed(() =>
    [...this.openOrders()].sort((a, b) => a.createdAt.localeCompare(b.createdAt)),
  );
  readonly toCollectCount = computed(() => this.openOrders().filter((order) => order.balanceDue > 0.001).length);

  readonly failedQueueCount = computed(() => this.queue().filter((entry) => entry.status === 'failed').length);

  async ngOnInit(): Promise<void> {
    // Accesos directos desde Inicio: /pedidos?tab=open abre directo "Pedidos abiertos".
    if (this.route.snapshot.queryParamMap.get('tab') === 'open') this.tab.set('open');
    try {
      const locationId = await this.locationResolver.resolve();
      this.resolvedLocationId = locationId;

      const [tables, categories, products, optionGroups] = await Promise.all([
        this.tableService.findAllByLocation(locationId),
        this.categoryService.findAll(),
        this.productService.findAll(),
        this.optionGroupService.findAll(),
        this.loadOpenOrders(locationId),
      ]);
      this.optionGroups.set(optionGroups);
      this.tables.set(tables.filter((table) => table.isActive).sort((a, b) => a.number - b.number));
      this.categories.set(
        categories.filter((category) => category.isActive).sort((a, b) => a.displayOrder - b.displayOrder),
      );
      this.products.set(products);

      void this.loadFloor(locationId);
      void this.loadChannels(locationId);

      this.realtime.connect(locationId);
      this.eventsSubscription = this.realtime.events$.subscribe((event) => this.upsertOpenOrder(event.data));
      this.pollHandle = setInterval(() => void this.loadOpenOrders(locationId), POLL_INTERVAL_MS);
      this.tickHandle = setInterval(() => this.now.set(Date.now()), CLOCK_TICK_MS);
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudo cargar la pantalla de pedidos.'));
    } finally {
      this.loading.set(false);
    }
  }

  ngOnDestroy(): void {
    this.eventsSubscription?.unsubscribe();
    if (this.pollHandle !== null) clearInterval(this.pollHandle);
    if (this.tickHandle !== null) clearInterval(this.tickHandle);
  }

  // ---- Paso 1: dónde ----

  chooseTakeaway(): void {
    this.diningOption.set('TAKEAWAY');
    this.selectedTableId.set(null);
    this.busyTableNotice.set(null);
    this.step.set(2);
  }

  chooseTable(table: TableResponse): void {
    if (this.busyTableIds().has(table.id)) {
      this.busyTableNotice.set(table);
      return;
    }
    this.busyTableNotice.set(null);
    this.diningOption.set('DINE_IN');
    this.selectedTableId.set(table.id);
    this.step.set(2);
  }

  // ---- V19: pedido de app ----

  /** Las apps prendidas del local. Si falla, no se muestra nada (el resto de la pantalla sigue igual). */
  private async loadChannels(locationId: string): Promise<void> {
    try {
      const channels = await this.salesChannelService.findAll(locationId);
      this.appChannels.set(channels.filter((c) => c.mode !== 'OFF'));
    } catch {
      this.appChannels.set([]);
    }
  }

  chooseApp(channel: SalesChannel): void {
    this.busyTableNotice.set(null);
    if (this.appChannel()?.id !== channel.id) {
      this.appRef.set('');
      this.appCustomer.set('');
      this.appPickup.set('');
      this.appPaidByApp.set(true);
    }
    this.appChannel.set(channel);
    this.appFormError.set(null);
    this.appFormOpen.set(true);
  }

  /** Confirma los datos del pedido de app y pasa a elegir los productos. */
  confirmApp(): void {
    const channel = this.appChannel();
    const ref = this.appRef().trim().replace(/^#+/, '').trim();
    if (!channel) return;
    if (!ref) {
      this.appFormError.set(`Poné el número del pedido que aparece en ${channel.name}.`);
      return;
    }
    const repeated = this.openOrders().some(
      (o) => o.channelId === channel.id && (o.channelOrderRef ?? '').toLowerCase() === ref.toLowerCase(),
    );
    if (repeated) {
      this.appFormError.set(`El pedido #${ref} de ${channel.name} ya está cargado: buscalo en "Pedidos abiertos".`);
      return;
    }
    this.appRef.set(ref);
    this.diningOption.set('APP');
    this.selectedTableId.set(null);
    this.appFormOpen.set(false);
    this.step.set(2);
  }

  /** "14:30" de hoy → ISO, para la hora de retiro. Vacío = sin hora. */
  private pickupIso(): string | null {
    const value = this.appPickup().trim();
    const match = /^(\d{1,2}):(\d{2})$/.exec(value);
    if (!match) return null;
    const date = new Date();
    date.setHours(Number(match[1]), Number(match[2]), 0, 0);
    return date.toISOString();
  }

  /** Tocar una mesa en el plano: libre → tomar el pedido; ocupada → sumarle productos. */
  pickFromPlan(key: string | null): void {
    const table = key?.startsWith('t:') ? this.tablesById()[key.slice(2)] : null;
    if (!table) return;
    if (this.busyTableIds().has(table.id)) this.addToBusyTable(table);
    else this.chooseTable(table);
  }

  setTableView(view: 'plan' | 'list'): void {
    this.tableView.set(view);
    try {
      localStorage.setItem(TABLE_VIEW_KEY, view);
    } catch {
      /* sin almacenamiento: no pasa nada */
    }
  }

  /** El plano es un extra: si no carga, queda la lista de siempre. */
  private async loadFloor(locationId: string): Promise<void> {
    try {
      const plan = await this.floorService.plan(locationId);
      this.floorAreas.set(plan.areas);
      this.floorItems.set(
        plan.items.map((i) => ({ key: i.id!, kind: i.kind, label: i.label, x: +i.x, y: +i.y, width: +i.width, height: +i.height })),
      );
      const areas: Record<string, string> = {};
      for (const i of plan.items) areas[i.id!] = i.areaId;
      this.floorItemAreas.set(areas);
      // Primera zona que tenga mesas.
      const withTables = plan.areas.find((a) => this.tables().some((t) => t.areaId === a.id));
      this.floorAreaId.set((withTables ?? plan.areas[0])?.id ?? null);
    } catch {
      this.floorAreas.set([]);
    }
  }

  tableLabel(table: TableResponse): string {
    return tableDisplayName(table);
  }

  whereLabel(): string {
    const adding = this.addingTo();
    if (adding) return this.orderTableLabel(adding);
    if (this.diningOption() === 'TAKEAWAY') return 'Para llevar';
    if (this.diningOption() === 'APP') {
      const channel = this.appChannel();
      return channel ? `${channel.name} #${this.appRef()}` : 'Pedido de app';
    }
    const id = this.selectedTableId();
    const table = id ? this.tablesById()[id] : null;
    return table ? tableDisplayName(table) : 'Elegí dónde';
  }

  /** Ir a un paso anterior desde el indicador de pasos (nunca adelante sin completar el actual). */
  goToStep(step: 1 | 2 | 3): void {
    // Agregando a un pedido abierto: el "dónde" ya está fijo (se cambia cancelando).
    if (step === 1 && !this.addingTo()) this.step.set(1);
    if (step === 2 && this.diningOption()) this.step.set(2);
    if (step === 3 && this.diningOption() && this.cart().length > 0) this.step.set(3);
  }

  isStepDone(step: 1 | 2 | 3): boolean {
    const current = this.step();
    if (current === 'sent') return true;
    return step < current;
  }

  // ---- Agregar a un pedido abierto (hoja de ruta, paso 1) ----

  /** Arranca la carga de productos para SUMARLOS a un pedido abierto. */
  startAddingTo(order: OrderResponse): void {
    this.cart.set([]);
    this.orderNotes.set('');
    this.searchTerm.set('');
    this.selectedCategoryId.set(null);
    this.busyTableNotice.set(null);
    this.sent.set(null);
    this.addingTo.set(order);
    this.diningOption.set(order.tableId ? 'DINE_IN' : 'TAKEAWAY');
    this.selectedTableId.set(order.tableId);
    this.tab.set('new');
    this.step.set(2);
  }

  /** Desde el aviso "esa mesa ya tiene un pedido": sumar a ESE pedido. */
  addToBusyTable(table: TableResponse): void {
    const order = this.openOrders().find((o) => o.tableId === table.id);
    if (order) this.startAddingTo(order);
  }

  cancelAdding(): void {
    this.startNewOrder();
  }

  // ---- Paso 2: productos ----

  selectCategory(categoryId: string | null): void {
    this.selectedCategoryId.set(categoryId);
  }

  quantityInCart(productId: string): number {
    return this.cartQuantities()[productId] ?? 0;
  }

  groupsOf(productId: string): OptionGroupResponse[] {
    return this.groupsByProduct()[productId] ?? [];
  }

  /** "Tamaño, Leche" — para avisar en la tarjeta que ese producto pregunta opciones. */
  groupNames(productId: string): string {
    return this.groupsOf(productId)
      .map((group) => group.name)
      .join(', ');
  }

  /** Sin opciones: suma directo. Con opciones: abre la ventana para elegirlas. */
  addToCart(product: ProductResponse): void {
    if (this.groupsOf(product.id).length > 0) {
      this.pickingProduct.set(product);
      return;
    }
    this.addLine(product, { optionIds: [], labels: [], unitPrice: product.price });
  }

  onOptionsPicked(picked: PickedOptions): void {
    const product = this.pickingProduct();
    this.pickingProduct.set(null);
    if (product) this.addLine(product, picked);
  }

  private addLine(product: ProductResponse, picked: PickedOptions): void {
    const key = lineKey(product.id, picked.optionIds);
    const current = this.cart();
    const index = current.findIndex((line) => line.key === key);
    if (index === -1) {
      this.cart.set([
        ...current,
        {
          key,
          productId: product.id,
          productName: product.name,
          optionIds: picked.optionIds,
          optionLabels: picked.labels,
          unitPrice: picked.unitPrice,
          quantity: 1,
          notes: '',
          showNotes: false,
        },
      ]);
    } else {
      this.cart.set(current.map((line, i) => (i === index ? { ...line, quantity: line.quantity + 1 } : line)));
    }
  }

  incrementLine(key: string): void {
    this.cart.set(this.cart().map((line) => (line.key === key ? { ...line, quantity: line.quantity + 1 } : line)));
  }

  decrementLine(key: string, event?: Event): void {
    event?.stopPropagation();
    const updated = this.cart()
      .map((line) => (line.key === key ? { ...line, quantity: line.quantity - 1 } : line))
      .filter((line) => line.quantity > 0);
    this.cart.set(updated);
    if (updated.length === 0 && this.step() === 3) this.step.set(2);
  }

  /** "−" en la tarjeta del producto: resta de la última línea de ese producto que se sumó. */
  decrementProduct(productId: string, event?: Event): void {
    const last = [...this.cart()].reverse().find((line) => line.productId === productId);
    if (last) this.decrementLine(last.key, event);
  }

  removeLine(key: string): void {
    this.cart.set(this.cart().filter((line) => line.key !== key));
    if (this.cart().length === 0 && this.step() === 3) this.step.set(2);
  }

  toggleLineNotes(key: string): void {
    this.cart.set(this.cart().map((line) => (line.key === key ? { ...line, showNotes: !line.showNotes } : line)));
  }

  updateLineNotes(key: string, notes: string): void {
    this.cart.set(this.cart().map((line) => (line.key === key ? { ...line, notes } : line)));
  }

  productInitials(name: string): string {
    return name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((word) => word[0])
      .join('')
      .toUpperCase();
  }

  // ---- Paso 3: enviar ----

  goToReview(): void {
    if (this.cart().length > 0) this.step.set(3);
  }

  submitOrder(): void {
    const locationId = this.resolvedLocationId;
    if (this.submitting() || this.cart().length === 0 || !locationId) return;
    const adding = this.addingTo();
    if (adding) {
      void this.submitAddition(adding);
      return;
    }
    if (this.diningOption() === 'DINE_IN' && !this.selectedTableId()) {
      this.step.set(1);
      this.toast.error('Elegí una mesa, o "Para llevar".');
      return;
    }
    this.submitting.set(true);
    try {
      const app = this.diningOption() === 'APP' ? this.appChannel() : null;
      const input: CreateOrderInput = {
        locationId,
        tableId: app ? null : this.selectedTableId(),
        channel: app
          ? {
              channelId: app.id,
              orderRef: this.appRef(),
              customerName: this.appCustomer().trim() || null,
              pickupAt: this.pickupIso(),
              paidByApp: this.appPaidByApp(),
            }
          : null,
        idempotencyKey: crypto.randomUUID(),
        clientTimestamp: new Date().toISOString(),
        notes: this.orderNotes().trim() || null,
        items: this.cart().map((line) => ({
          productId: line.productId,
          quantity: line.quantity,
          notes: line.notes.trim() || null,
          optionIds: line.optionIds,
        })),
      };
      this.offlineQueue.enqueue(input);
      this.sent.set({
        tableLabel: this.whereLabel(),
        itemCount: this.cartItemCount(),
        total: this.cartTotal(),
        online: this.queueIsOnline(),
        added: false,
      });
      this.step.set('sent');
      this.toast.success(this.queueIsOnline() ? 'Pedido enviado a cocina.' : 'Pedido guardado: se envía al volver la conexión.');
    } finally {
      this.submitting.set(false);
    }
  }

  /**
   * Suma el carrito a un pedido abierto. A diferencia de un pedido nuevo,
   * NO pasa por la cola offline: sumar a un pedido que en el servidor
   * pudo haberse cobrado/cerrado mientras tanto necesita la respuesta en el
   * momento (si ya se cerró, avisa y no suma nada).
   */
  private async submitAddition(order: OrderResponse): Promise<void> {
    if (!this.queueIsOnline()) {
      this.toast.error('Sin internet: para sumar a un pedido abierto hace falta conexión.');
      return;
    }
    this.submitting.set(true);
    try {
      const updated = await this.orderService.addItems(order.id, {
        items: this.cart().map((line) => ({
          productId: line.productId,
          quantity: line.quantity,
          notes: line.notes.trim() || null,
          optionIds: line.optionIds,
        })),
      });
      this.upsertOpenOrder(updated);
      this.sent.set({
        tableLabel: this.orderTableLabel(updated),
        itemCount: this.cartItemCount(),
        total: this.cartTotal(),
        online: true,
        added: true,
      });
      this.step.set('sent');
      this.toast.success(`Agregado al pedido de ${this.orderTableLabel(updated)}.`);
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo agregar al pedido.'));
    } finally {
      this.submitting.set(false);
    }
  }

  startNewOrder(): void {
    this.addingTo.set(null);
    this.cart.set([]);
    this.orderNotes.set('');
    this.diningOption.set(null);
    this.selectedTableId.set(null);
    this.appChannel.set(null);
    this.appRef.set('');
    this.appCustomer.set('');
    this.appPickup.set('');
    this.appPaidByApp.set(true);
    this.selectedCategoryId.set(null);
    this.searchTerm.set('');
    this.sent.set(null);
    this.busyTableNotice.set(null);
    this.step.set(1);
  }

  clearCart(): void {
    this.cart.set([]);
    this.orderNotes.set('');
    if (this.step() === 3) this.step.set(2);
  }

  // ---- Cola offline ----

  queueEntryTableLabel(entry: QueuedOrder): string {
    const channel = entry.request.channel;
    if (channel) {
      const name = this.appChannels().find((c) => c.id === channel.channelId)?.name ?? 'App';
      return `${name} #${channel.orderRef}`;
    }
    const tableId = entry.request.tableId;
    if (!tableId) return 'Para llevar';
    const table = this.tablesById()[tableId];
    return table ? tableDisplayName(table) : 'Mesa';
  }

  queueEntryItemCount(entry: QueuedOrder): number {
    return entry.request.items.reduce((sum, item) => sum + item.quantity, 0);
  }

  queueStatusLabel(entry: QueuedOrder): string {
    switch (entry.status) {
      case 'pending':
        return this.queueIsOnline() ? 'En cola' : 'Esperando conexión';
      case 'syncing':
        return 'Enviando…';
      case 'failed':
        return 'No se pudo enviar';
    }
  }

  retryQueueEntry(localId: string): void {
    this.offlineQueue.retry(localId);
  }

  removeQueueEntry(localId: string): void {
    this.offlineQueue.remove(localId);
  }

  // ---- Pedidos abiertos ----

  orderTableLabel(order: OrderResponse): string {
    const app = channelLabel(order);
    if (app) return app;
    if (!order.tableId) return 'Para llevar';
    const table = this.tablesById()[order.tableId];
    return table ? tableDisplayName(table) : 'Mesa';
  }

  elapsedMinutes(order: OrderResponse): number {
    return Math.max(0, Math.floor((this.now() - new Date(order.createdAt).getTime()) / 60000));
  }

  itemsSummary(order: OrderResponse): string {
    return order.items
      .map((item) => {
        const options = itemOptionsLabel(item);
        return `${item.quantity}× ${item.productName}${options ? ` (${options})` : ''}`;
      })
      .join(', ');
  }

  isPaid(order: OrderResponse): boolean {
    return order.balanceDue <= 0.001;
  }

  canDeliver(order: OrderResponse): boolean {
    const role = this.role();
    return (
      order.status === 'READY' && !!role && allowedRolesForTransition(order.status, 'DELIVERED').includes(role)
    );
  }

  openPayment(order: OrderResponse): void {
    this.payingOrder.set(order);
  }

  onPaymentUpdated(order: OrderResponse): void {
    this.upsertOpenOrder(order);
  }

  async deliver(order: OrderResponse): Promise<void> {
    if (this.pendingActionId()) return;
    if (!this.isPaid(order)) {
      this.openPayment(order);
      return;
    }
    this.pendingActionId.set(order.id);
    try {
      const updated = await this.orderService.updateStatus(order.id, { status: 'DELIVERED' });
      this.upsertOpenOrder(updated);
      this.toast.success(`${this.orderTableLabel(order)}: pedido entregado.`);
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo marcar como entregado.'));
    } finally {
      this.pendingActionId.set(null);
    }
  }

  async printCustomerTicket(order: OrderResponse): Promise<void> {
    await this.print(order, 'customer');
  }

  async printKitchenTicket(order: OrderResponse): Promise<void> {
    await this.print(order, 'kitchen');
  }

  private async print(order: OrderResponse, kind: 'customer' | 'kitchen'): Promise<void> {
    try {
      const context = await this.printContext.get(order.locationId);
      const label = this.orderTableLabel(order);
      this.ticketPrint.print(
        kind === 'customer' ? buildCustomerTicket(order, label, context) : buildKitchenTicket(order, label, context),
      );
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo imprimir.'));
    }
  }

  private async loadOpenOrders(locationId: string): Promise<void> {
    try {
      this.openOrders.set(await this.orderService.findActiveByLocation(locationId));
    } catch {
      // El polling es respaldo: si falla una vuelta, se reintenta en la próxima.
    }
  }

  private upsertOpenOrder(order: OrderResponse): void {
    const current = this.openOrders();
    if (CLOSED_STATUSES.includes(order.status)) {
      this.openOrders.set(current.filter((existing) => existing.id !== order.id));
      return;
    }
    const index = current.findIndex((existing) => existing.id === order.id);
    this.openOrders.set(
      index === -1 ? [...current, order] : current.map((existing) => (existing.id === order.id ? order : existing)),
    );
    // Si la ventana de cobro está abierta sobre este pedido, que vea el saldo nuevo.
    if (this.payingOrder()?.id === order.id) this.payingOrder.set(order);
  }
}

function readTableView(): 'plan' | 'list' {
  try {
    return localStorage.getItem(TABLE_VIEW_KEY) === 'list' ? 'list' : 'plan';
  } catch {
    return 'plan';
  }
}
