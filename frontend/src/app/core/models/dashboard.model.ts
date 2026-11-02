import { PaymentMethod } from './order.model';
import { IngredientStockLevelResponse, ProductStockLevelResponse } from './stock.model';

/** Espejo de {@code dashboard/dto/TopProductResponse.java} — Bloque E. */
export interface TopProductResponse {
  productId: string;
  productName: string;
  quantity: number;
  revenue: number;
}

/**
 * Espejo de {@code dashboard/dto/DashboardSummaryResponse.java} — KPIs de
 * UN local en el rango {@code [from, to]} pedido (Bloque E). Todo ya viene
 * calculado por el backend; el frontend solo pinta.
 */
export interface DashboardSummaryResponse {
  locationId: string;
  from: string;
  to: string;
  totalSales: number;
  orderCount: number;
  averageTicket: number;
  topProducts: TopProductResponse[];
  salesByMethod: Partial<Record<PaymentMethod, number>>;
  lowIngredients: IngredientStockLevelResponse[];
  lowProducts: ProductStockLevelResponse[];
}

/** Períodos predefinidos del selector — el front calcula el rango real de fechas, ver `DashboardComponent#periodRange`. */
export type DashboardPeriod = 'today' | 'week' | 'month';

export const DASHBOARD_PERIOD_LABELS: Readonly<Record<DashboardPeriod, string>> = {
  today: 'Hoy',
  week: 'Esta semana',
  month: 'Este mes',
};
