import { TableResponse, TableShape } from './table.model';

/**
 * Plano del salón (V18). Espejo de {@code floor/dto/*} del backend.
 *
 * Cada zona es un lienzo de {@link FLOOR_WIDTH} x {@link FLOOR_HEIGHT}
 * unidades (proporción 4:3); x/y es la esquina de arriba a la izquierda.
 * En pantalla se dibuja en porcentajes, así se ve igual en la compu, la
 * tablet y el celular.
 */
export const FLOOR_WIDTH = 100;
export const FLOOR_HEIGHT = 75;
export const FLOOR_MIN_SIZE = 4; // el backend acepta desde 2; acá, desde 4 para que se pueda tocar

export type FloorItemKind = 'BAR' | 'DOOR' | 'WALL' | 'WINDOW' | 'RESTROOM' | 'KITCHEN';

export const FLOOR_ITEM_KINDS: ReadonlyArray<{ kind: FloorItemKind; label: string; w: number; h: number }> = [
  { kind: 'BAR', label: 'Barra', w: 30, h: 8 },
  { kind: 'DOOR', label: 'Puerta', w: 12, h: 4 },
  { kind: 'WALL', label: 'Pared', w: 30, h: 2 },
  { kind: 'WINDOW', label: 'Ventana', w: 20, h: 2 },
  { kind: 'RESTROOM', label: 'Baños', w: 14, h: 12 },
  { kind: 'KITCHEN', label: 'Cocina', w: 22, h: 16 },
];

export function floorItemLabel(kind: FloorItemKind): string {
  return FLOOR_ITEM_KINDS.find((k) => k.kind === kind)?.label ?? kind;
}

export const TABLE_SHAPE_LABELS: Readonly<Record<TableShape, string>> = {
  ROUND: 'Redonda',
  SQUARE: 'Cuadrada',
  RECT: 'Rectangular',
};

export interface FloorArea {
  id: string;
  name: string;
  position: number;
}

export interface FloorItem {
  id: string | null;
  areaId: string;
  kind: FloorItemKind;
  label: string | null;
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface FloorPlan {
  areas: FloorArea[];
  items: FloorItem[];
  tables: TableResponse[];
}

export interface SaveLayoutInput {
  tables: { id: string; x: number; y: number; width: number; height: number; shape: TableShape }[];
  items: { id: string | null; kind: FloorItemKind; label: string | null; x: number; y: number; width: number; height: number }[];
}

/** Una caja en el lienzo (mesa o referencia). */
export interface FloorBox {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** ¿La mesa está ubicada en esta zona? */
export function isPlacedIn(table: TableResponse, areaId: string): boolean {
  return table.areaId === areaId && table.x !== null && table.y !== null && table.width !== null && table.height !== null;
}
