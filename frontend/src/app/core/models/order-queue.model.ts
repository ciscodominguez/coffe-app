import { CreateOrderInput } from './order.model';

/**
 * Un pedido cargado por una mesera, tal como vive en la cola offline del
 * navegador (ver OfflineOrderQueueService) — independiente de si el
 * envío al backend ya se resolvió o no.
 *
 * `localId` es un identificador puramente local (nunca lo ve el backend,
 * a diferencia de `request.idempotencyKey`, que sí viaja en el POST):
 * sirve para poder listar/reintentar/quitar una entrada puntual de la
 * cola desde la UI antes de que exista una respuesta del servidor.
 */
export type QueuedOrderStatus = 'pending' | 'syncing' | 'failed';

export interface QueuedOrder {
  localId: string;
  request: CreateOrderInput;
  createdAt: string;
  status: QueuedOrderStatus;
  /** Solo tiene sentido cuando `status === 'failed'` — motivo del último intento fallido, para mostrarlo en la UI. */
  errorMessage?: string;
  attempts: number;
}
