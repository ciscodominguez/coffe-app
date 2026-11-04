/**
 * Variantes y agregados (V11) — espejo de option/dto del backend.
 *
 * Un grupo ("Leche") se carga una vez y se asigna a varios productos. Al
 * tomar un pedido, para cada producto con grupos se abre la ventana para
 * elegir (ver OptionPickerComponent).
 */

export interface OptionItemResponse {
  id: string;
  name: string;
  /** Lo que suma al precio del producto (0 = sin cargo). */
  priceDelta: number;
  ingredientId: string | null;
  ingredientName: string | null;
  ingredientUnit: string | null;
  ingredientQuantity: number | null;
  /** Viene marcada de entrada al elegir. */
  isDefault: boolean;
  /** false = "no hay por hoy": no se ofrece. */
  isAvailable: boolean;
  position: number;
}

export interface OptionGroupResponse {
  id: string;
  name: string;
  /** 0 = opcional; 1 o más = hay que elegir al menos esa cantidad. */
  minSelect: number;
  /** null = sin límite. */
  maxSelect: number | null;
  options: OptionItemResponse[];
  productIds: string[];
  createdAt: string;
  updatedAt: string;
}

export interface OptionItemInput {
  id?: string | null;
  name: string;
  priceDelta: number;
  ingredientId?: string | null;
  ingredientQuantity?: number | null;
  isDefault: boolean;
  isAvailable: boolean;
}

export interface SaveOptionGroupInput {
  name: string;
  minSelect: number;
  maxSelect: number | null;
  options: OptionItemInput[];
  /** Productos que usan el grupo (reemplaza la lista). Sin el campo, no se tocan. */
  productIds?: string[];
}

/** Opciones que se pueden elegir hoy (las marcadas "no hay" no se ofrecen). */
export function availableOptions(group: OptionGroupResponse): OptionItemResponse[] {
  return group.options.filter((option) => option.isAvailable);
}

/**
 * Mínimo real al tomar un pedido: si en un grupo obligatorio no queda
 * ninguna opción disponible, deja de ser obligatorio (mismo criterio que
 * `OptionSelector` del backend: no se traba la venta).
 */
export function effectiveMin(group: OptionGroupResponse): number {
  return Math.min(group.minSelect, availableOptions(group).length);
}

/** La regla del grupo en palabras: "Obligatorio · elegí 1", "Opcional · hasta 2"… */
export function groupRuleLabel(group: { minSelect: number; maxSelect: number | null }): string {
  const { minSelect: min, maxSelect: max } = group;
  if (min === 0) {
    if (max === 1) return 'Opcional · una sola';
    if (max === null) return 'Opcional · las que quieras';
    return `Opcional · hasta ${max}`;
  }
  if (max === min) return min === 1 ? 'Obligatorio · elegí 1' : `Obligatorio · elegí ${min}`;
  if (max === null) return `Obligatorio · al menos ${min}`;
  return `Obligatorio · de ${min} a ${max}`;
}
