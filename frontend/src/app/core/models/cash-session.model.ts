import { PaymentMethod } from './order.model';

/** Espejo de {@code domain/enums/CashSessionStatus.java}. */
export type CashSessionStatus = 'OPEN' | 'CLOSED';

/** Espejo de {@code domain/enums/CashMovementType.java} — ítem 14. */
export type CashMovementType = 'INCOME' | 'EXPENSE';

export const CASH_MOVEMENT_TYPE_LABELS: Readonly<Record<CashMovementType, string>> = {
  INCOME: 'Ingreso',
  EXPENSE: 'Egreso',
};

/** Espejo de {@code cash/dto/CashMovementResponse.java}. `createdByName` — mejora "nombres reales". */
export interface CashMovementResponse {
  id: string;
  cashSessionId: string;
  type: CashMovementType;
  amount: number;
  reason: string;
  createdById: string;
  createdByName: string | null;
  createdAt: string;
}

/** Espejo de {@code order/dto/OrderPaymentResponse.java} — una línea de cobro, usada acá para el detalle del turno. */
export interface CashSessionPaymentResponse {
  id: string;
  orderId: string;
  cashSessionId: string;
  paymentMethod: PaymentMethod;
  amount: number;
  createdById: string;
  createdByName: string | null;
  createdAt: string;
}

/** Espejo de {@code cash/dto/DenominationCountResponse.java} — "conteo de efectivo por billete". */
export interface DenominationCountResponse {
  denomination: number;
  quantity: number;
  subtotal: number;
}

/**
 * Espejo de {@code cash/dto/CashSessionResponse.java} — los totales
 * (`salesByMethod`, `expectedCashAmount`, etc.) ya vienen calculados por el
 * backend, nunca se recalculan acá (ver el Javadoc de `CashSessionService`
 * del backend: se recalculan en cada lectura a partir de los pagos/
 * movimientos reales, así nunca quedan desincronizados).
 *
 * <p>`openedByName`/`closedByName`, `payments` (detalle línea por línea de
 * cobros del turno) y `denominationBreakdown` son las mejoras "caja más
 * profesional" pedidas por Fran.
 */
export interface CashSessionResponse {
  id: string;
  locationId: string;
  status: CashSessionStatus;
  openedById: string;
  openedByName: string | null;
  openedAt: string;
  openingAmount: number;
  closedById: string | null;
  closedByName: string | null;
  closedAt: string | null;
  countedCashAmount: number | null;
  postnetBatchNumber: string | null;
  postnetReportedAmount: number | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  salesByMethod: Partial<Record<PaymentMethod, number>>;
  totalSales: number;
  cashIncomeMovementsTotal: number;
  cashExpenseMovementsTotal: number;
  expectedCashAmount: number;
  cashDifference: number | null;
  movements: CashMovementResponse[];
  payments: CashSessionPaymentResponse[];
  denominationBreakdown: DenominationCountResponse[];
}

/**
 * Billetes vigentes en Argentina que acepta el conteo por billete (espejo
 * EXACTO de {@code CashSessionService.KNOWN_DENOMINATIONS} del backend —
 * confirmado vía BCRA/prensa, septiembre 2026). Monedas y billetes chicos
 * quedan afuera por la devaluación (inviables para contar en caja).
 */
export const CASH_DENOMINATIONS: readonly number[] = [20000, 10000, 2000, 1000, 500, 200, 100, 50, 20, 10];

/** Espejo de {@code cash/dto/DenominationCountLine.java} — una línea del conteo al cerrar. */
export interface DenominationCountLineInput {
  denomination: number;
  quantity: number;
}

/** Espejo de {@code cash/dto/OpenCashSessionRequest.java}. */
export interface OpenCashSessionInput {
  locationId: string;
  openingAmount: number;
  notes?: string | null;
}

/** Espejo de {@code cash/dto/CreateCashMovementRequest.java}. */
export interface CreateCashMovementInput {
  type: CashMovementType;
  amount: number;
  reason: string;
}

/**
 * Espejo de {@code cash/dto/CloseCashSessionRequest.java} — ítem 15: lote
 * de Postnet opcional. `denominationBreakdown` es la mejora "conteo de
 * efectivo por billete": cuando se manda con líneas, el backend recalcula
 * `countedCashAmount` a partir de ese desglose e ignora el valor suelto
 * (ver el Javadoc de `CashSessionService#close` del backend) — igual sigue
 * siendo obligatorio para no romper un cierre "a la vieja".
 */
export interface CloseCashSessionInput {
  countedCashAmount: number;
  denominationBreakdown?: DenominationCountLineInput[];
  postnetBatchNumber?: string | null;
  postnetReportedAmount?: number | null;
  notes?: string | null;
}
