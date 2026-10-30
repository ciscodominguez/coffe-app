import { Injectable } from '@angular/core';
import { AddOrderItemsInput, ApplyDiscountInput, CreateOrderInput, CreateOrderPaymentInput, OrderResponse, UpdateOrderStatusInput } from '../models/order.model';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class OrderService {
  constructor(private readonly api: ApiService) {}

  /** Todos los pedidos (cualquier estado) de un local — usado por Meseras para el panel de "pedidos recientes". */
  findAllByLocation(locationId: string): Promise<OrderResponse[]> {
    return this.api.get<OrderResponse[]>('/orders', { locationId });
  }

  /** Respaldo por polling del KDS — mismo subconjunto (pedidos abiertos) que el tópico WebSocket de ese local. */
  findActiveByLocation(locationId: string): Promise<OrderResponse[]> {
    return this.api.get<OrderResponse[]>('/orders/active', { locationId });
  }

  /**
   * `idempotencyKey` ya viaja dentro de `input` (ver CreateOrderInput) —
   * lo genera quien encola el pedido (OfflineOrderQueueService), no acá,
   * porque tiene que ser el MISMO valor en todos los reintentos de un
   * mismo pedido encolado.
   */
  create(input: CreateOrderInput): Promise<OrderResponse> {
    return this.api.post<OrderResponse>('/orders', input);
  }

  updateStatus(id: string, input: UpdateOrderStatusInput): Promise<OrderResponse> {
    return this.api.patch<OrderResponse>(`/orders/${id}/status`, input);
  }

  /** Ítems 5/11: una línea de cobro por llamada — para pago dividido se llama una vez por método. */
  /** Sumar productos a un pedido abierto (hoja de ruta, paso 1). Requiere conexión: no pasa por la cola offline. */
  addItems(id: string, input: AddOrderItemsInput): Promise<OrderResponse> {
    return this.api.post<OrderResponse>(`/orders/${id}/items`, input);
  }

  /** V12 — aplicar un descuento (a un ítem o a todo el pedido). Si ya había uno en ese lugar, se reemplaza. */
  applyDiscount(id: string, input: ApplyDiscountInput): Promise<OrderResponse> {
    return this.api.post<OrderResponse>(`/orders/${id}/discounts`, input);
  }

  removeDiscount(id: string, orderDiscountId: string): Promise<OrderResponse> {
    return this.api.delete<OrderResponse>(`/orders/${id}/discounts/${orderDiscountId}`);
  }

  addPayment(id: string, input: CreateOrderPaymentInput): Promise<OrderResponse> {
    return this.api.post<OrderResponse>(`/orders/${id}/payments`, input);
  }
}
