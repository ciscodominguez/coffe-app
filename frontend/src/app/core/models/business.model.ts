export interface BusinessResponse {
  id: string;
  legalName: string;
  tradeName: string | null;
  taxId: string;
  fiscalCondition: string;
  plan: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

/** Vista mínima abierta a cualquier rol (GET /businesses/me/display-name). */
export interface BusinessDisplayNameResponse {
  displayName: string;
}

export interface UpdateBusinessInput {
  legalName?: string;
  tradeName?: string;
  fiscalCondition?: string;
}

/** Anchos de rollo térmico soportados — espejo del CHECK de la V7 y de `LocationService.SUPPORTED_PAPER_WIDTHS_MM`. */
export type ReceiptPaperWidth = 58 | 80;

export const RECEIPT_PAPER_WIDTHS: readonly ReceiptPaperWidth[] = [80, 58];

export interface LocationResponse {
  id: string;
  name: string;
  address: string | null;
  phone: string | null;
  /** Ancho de la ticketera del local — lo usa TicketPrintService (Etapa 3, impresión térmica). */
  receiptPaperWidthMm: ReceiptPaperWidth;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateLocationInput {
  name?: string;
  address?: string;
  phone?: string;
  receiptPaperWidthMm?: ReceiptPaperWidth;
}
