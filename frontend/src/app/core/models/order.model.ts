import { SalesChannelKind } from './sales-channel.model';
import { UserRole } from './auth.model';

/**
 * Espejo de order/dto (OrderResponse/OrderItemResponse) y del envoltorio
 * {event, data} que viaja por el tópico STOMP (ver OrderEventMessage.java
 * / OrderTopics.java) del backend — mismo criterio que auth.model.ts: un
 * solo lugar de verdad para lo que consume el frontend.
 */

export type OrderStatus = 'PENDING' | 'IN_PREPARATION' | 'READY' | 'DELIVERED' | 'CANCELLED';

export const ORDER_STATUS_LABELS: Readonly<Record<OrderStatus, string>> = {
  PENDING: 'Nuevo',
  IN_PREPARATION: 'En preparación',
  READY: 'Listo',
  DELIVERED: 'Entregado',
  CANCELLED: 'Cancelado',
};

export interface OrderItemResponse {
  id: string;
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  discountAmount: number;
  subtotal: number;
  notes: string | null;
  /** Tanda (V8): 1 = pedido original; 2, 3… = agregados después (ver `OrderService#addItems`). */
  batch: number;
  /** V11 — lo elegido (tamaño, leche, extras…). `unitPrice` ya incluye los extras. */
  options?: OrderItemOptionResponse[];
  /** V14 — unidades ya cobradas "por productos". */
  paidQuantity?: number;
  createdAt: string;
}

/** Una opción elegida en un ítem, tal como se copió al venderlo (V11). */
export interface OrderItemOptionResponse {
  optionId: string | null;
  groupName: string;
  name: string;
  priceDelta: number;
}

/** Las opciones de un ítem en una línea: "Grande · Almendra · Extra shot" ('' si no tiene). */
export function itemOptionsLabel(item: { options?: OrderItemOptionResponse[] | null }): string {
  return (item.options ?? []).map((option) => option.name).join(' · ');
}

/** Tanda más alta del pedido — si es mayor a 1, al pedido se le agregaron cosas. */
export function latestBatch(order: { items: OrderItemResponse[] }): number {
  return order.items.reduce((max, item) => Math.max(max, item.batch ?? 1), 1);
}

/** Espejo de {@code domain/enums/PaymentMethod.java} — Bloque C (caja), ítems 5/11. */
export type PaymentMethod = 'CASH' | 'DEBIT_CARD' | 'CREDIT_CARD' | 'TRANSFER' | 'QR' | 'OTHER' | 'APP';

export const PAYMENT_METHOD_LABELS: Readonly<Record<PaymentMethod, string>> = {
  CASH: 'Efectivo',
  DEBIT_CARD: 'Débito',
  CREDIT_CARD: 'Crédito',
  TRANSFER: 'Transferencia',
  QR: 'QR',
  OTHER: 'Otro',
  APP: 'App de delivery',
};

/** Espejo de {@code order/dto/OrderPaymentResponse.java} — una línea de cobro de un pedido. `createdByName` — mejora "nombres reales". */
export interface OrderPaymentResponse {
  id: string;
  orderId: string;
  cashSessionId: string;
  paymentMethod: PaymentMethod;
  amount: number;
  createdById: string;
  createdByName: string | null;
  /** V14 — si fue un cobro "por productos": qué pagó. */
  items?: OrderPaymentItemResponse[];
  createdAt: string;
}

/** V14 — un renglón pagado por un cobro "por productos". */
export interface OrderPaymentItemResponse {
  orderItemId: string;
  quantity: number;
  amount: number;
}

/** V14 — qué paga un cobro "por productos". */
export interface PaymentItemInput {
  orderItemId: string;
  quantity: number;
}

/**
 * `payments`/`paidTotal`/`balanceDue` reemplazan al antiguo campo único
 * `paymentMethod` (ítems 5/11 — ver el Javadoc de `OrderResponse.java`):
 * un pedido puede tener varias líneas de pago, cada una con su método.
 */
/** Descuento en porcentaje o en monto fijo (V12). */
export type DiscountType = 'PERCENTAGE' | 'FIXED_AMOUNT';

/** "10%" o "$ 500". */
export function discountValueLabel(discount: { type: DiscountType; value: number }): string {
  return discount.type === 'PERCENTAGE'
    ? `${discount.value.toLocaleString('es-AR', { maximumFractionDigits: 2 })}%`
    : `$ ${discount.value.toLocaleString('es-AR', { maximumFractionDigits: 2 })}`;
}

/** Un descuento aplicado al pedido (V12). `orderItemId` null = sobre todo el pedido. */
export interface OrderDiscountResponse {
  id: string;
  orderItemId: string | null;
  discountId: string | null;
  name: string;
  type: DiscountType;
  value: number;
  /** Lo que descuenta de verdad. */
  amount: number;
  /** A mano o "pide autorización": solo supervisor/admin/dueño lo quita. */
  supervisorOnly: boolean;
  reason: string | null;
  createdById: string;
  createdByName: string | null;
  createdAt: string;
}

/** Espejo de {@code order/dto/ApplyDiscountRequest.java}: de la lista (`discountId`) o a mano (tipo + valor + motivo). */
export interface ApplyDiscountInput {
  orderItemId?: string | null;
  discountId?: string | null;
  type?: DiscountType;
  value?: number;
  reason?: string | null;
}

/** Todo lo descontado en el pedido (por ítem + sobre el pedido). */
export function totalDiscounted(order: { discountTotal: number; items: { discountAmount: number }[] }): number {
  return order.discountTotal + order.items.reduce((sum, item) => sum + (item.discountAmount ?? 0), 0);
}

export interface OrderResponse {
  id: string;
  locationId: string;
  tableId: string | null;
  createdById: string;
  status: OrderStatus;
  subtotal: number;
  discountTotal: number;
  total: number;
  notes: string | null;
  idempotencyKey: string;
  clientTimestamp: string;
  createdAt: string;
  updatedAt: string;
  closedAt: string | null;
  items: OrderItemResponse[];
  payments: OrderPaymentResponse[];
  /** V12 — descuentos aplicados. */
  discounts?: OrderDiscountResponse[];
  paidTotal: number;
  balanceDue: number;
  /** V19 — pedido de una app de delivery (todo null en un pedido común). */
  channelId?: string | null;
  channelKind?: SalesChannelKind | null;
  channelName?: string | null;
  channelOrderRef?: string | null;
  channelCustomer?: string | null;
  channelPickupAt?: string | null;
  channelPaidByApp?: boolean | null;
  channelCommissionPct?: number | null;
}

/** "PedidosYa #1234" — cómo se nombra un pedido de app en todas las pantallas. */
export function channelLabel(order: { channelName?: string | null; channelOrderRef?: string | null }): string | null {
  return order.channelName ? `${order.channelName} #${order.channelOrderRef ?? ''}` : null;
}

export interface UpdateOrderStatusInput {
  status: OrderStatus;
}

/** Espejo de {@code order/dto/CreateOrderPaymentRequest.java}. */
export interface CreateOrderPaymentInput {
  paymentMethod: PaymentMethod;
  /** Obligatorio salvo que vengan `items` (ahí lo calcula el backend). */
  amount?: number;
  /** V14 — dividir por lo que consumió cada uno. */
  items?: PaymentItemInput[];
}

/**
 * Espejo de {@code order/dto/CreateOrderItemRequest.java}. Sin precio ni
 * nombre a propósito — el backend los congela leyendo el Product real
 * (ver el Javadoc de ese DTO en el backend), nunca confía en lo que mande el
 * cliente para eso.
 */
export interface CreateOrderItemInput {
  productId: string;
  quantity: number;
  notes?: string | null;
  /** V11 — ids de las opciones elegidas. El backend calcula el precio con ellas. */
  optionIds?: string[];
}

/**
 * Espejo de {@code order/dto/CreateOrderRequest.java}. `idempotencyKey` +
 * `clientTimestamp` existen en el backend justamente para la cola offline
 * del frontend (ver el Javadoc de ese DTO en el backend) — acá es donde por fin
 * se usan para eso: cada pedido encolado localmente (ver
 * OfflineOrderQueueService) genera su propio UUID una sola vez, al
 * momento de cargarlo, y lo reusa en todos los reintentos de envío, así
 * un reintento después de un corte de red nunca duplica el pedido del
 * lado del servidor.
 */
export interface CreateOrderInput {
  locationId: string;
  tableId?: string | null;
  idempotencyKey: string;
  clientTimestamp: string;
  notes?: string | null;
  items: CreateOrderItemInput[];
  /** V19 — solo para pedidos que vinieron por una app de delivery. */
  channel?: ChannelOrderInput | null;
}

/** Espejo de {@code order/dto/ChannelOrderInput.java}. */
export interface ChannelOrderInput {
  channelId: string;
  orderRef: string;
  customerName?: string | null;
  pickupAt?: string | null;
  /** true = lo cobró la app; false = lo cobra el café. */
  paidByApp?: boolean;
}

/** Espejo de {@code order/dto/AddOrderItemsRequest.java} — sumar productos a un pedido abierto. */
export interface AddOrderItemsInput {
  items: CreateOrderItemInput[];
}

/** Mismo vocabulario que {@code OrderEventMessage.java} del backend. */
export type OrderEventName = 'order:created' | 'order:status_changed' | 'order:updated';

export interface OrderEventMessage {
  event: OrderEventName;
  data: OrderResponse;
}

const KITCHEN_ROLES: readonly UserRole[] = ['KITCHEN'];
const FRONT_OF_HOUSE_ROLES: readonly UserRole[] = ['WAITER', 'SUPERVISOR', 'ADMIN', 'OWNER'];

/**
 * Espejo EXACTO de {@code OrderService#allowedRolesFor} del backend — el
 * botón de "siguiente estado" del KDS solo se muestra cuando el rol
 * logueado realmente puede ejecutar esa transición puntual. Es solo UX:
 * el backend vuelve a validar esto mismo y rechaza con 403 si alguien la
 * fuerza igual (por ejemplo, pegándole directo a la API).
 */
export function allowedRolesForTransition(from: OrderStatus, to: OrderStatus): readonly UserRole[] {
  switch (from) {
    case 'PENDING':
      if (to === 'IN_PREPARATION') return KITCHEN_ROLES;
      if (to === 'CANCELLED') return FRONT_OF_HOUSE_ROLES;
      return [];
    case 'IN_PREPARATION':
      if (to === 'READY') return KITCHEN_ROLES;
      if (to === 'CANCELLED') return FRONT_OF_HOUSE_ROLES;
      return [];
    case 'READY':
      if (to === 'DELIVERED' || to === 'CANCELLED') return FRONT_OF_HOUSE_ROLES;
      return [];
    default:
      // DELIVERED/CANCELLED son terminales.
      return [];
  }
}

/** Próximo estado "de avance" (nunca CANCELLED) en la máquina de estados, o null si ya es terminal. */
export function nextStatus(status: OrderStatus): OrderStatus | null {
  switch (status) {
    case 'PENDING':
      return 'IN_PREPARATION';
    case 'IN_PREPARATION':
      return 'READY';
    case 'READY':
      return 'DELIVERED';
    default:
      return null;
  }
}
