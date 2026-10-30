/** Espejo de {@code table/dto/TableResponse.java} del backend. */

export type TableStatus = 'FREE' | 'OCCUPIED' | 'RESERVED';

/** V18 — forma de la mesa en el plano del salón. */
export type TableShape = 'ROUND' | 'SQUARE' | 'RECT';

export const TABLE_STATUS_LABELS: Readonly<Record<TableStatus, string>> = {
  FREE: 'Libre',
  OCCUPIED: 'Ocupada',
  RESERVED: 'Reservada',
};

export interface TableResponse {
  id: string;
  locationId: string;
  number: number;
  label: string | null;
  capacity: number | null;
  status: TableStatus;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
  /** V18 — dónde está en el plano (null = sin ubicar). Unidades del lienzo de 100 x 75. */
  areaId: string | null;
  x: number | null;
  y: number | null;
  width: number | null;
  height: number | null;
  shape: TableShape;
}

/** Espejo de {@code table/dto/CreateTableRequest.java}. */
export interface CreateTableInput {
  locationId: string;
  number: number;
  label?: string | null;
  capacity?: number | null;
}

/** Espejo de {@code table/dto/UpdateTableRequest.java}. */
export interface UpdateTableInput {
  number?: number;
  label?: string | null;
  capacity?: number | null;
  status?: TableStatus;
  isActive?: boolean;
}

/** "Mesa 4" o "Ventana (Mesa 4)" — mismo formato en todas las pantallas. */
export function tableDisplayName(table: TableResponse): string {
  return table.label ? `${table.label} (Mesa ${table.number})` : `Mesa ${table.number}`;
}
