import { ChangeDetectionStrategy, Component, ElementRef, input, output, viewChild } from '@angular/core';
import { FLOOR_HEIGHT, FLOOR_MIN_SIZE, FLOOR_WIDTH, FloorBox, FloorItemKind, floorItemLabel } from '../../../core/models/floor.model';
import { TableShape } from '../../../core/models/table.model';

/** Cómo se ve una mesa en el plano (lo arma la pantalla que lo usa). */
export interface CanvasTable extends FloorBox {
  id: string;
  number: number;
  label: string | null;
  shape: TableShape;
  /** Color: libre (verde), ocupada, demorada (+45 min), reservada o neutro (editor). */
  tone: 'free' | 'busy' | 'late' | 'reserved' | 'neutral';
  /** Renglones chicos debajo del número (ej. "32 min", "$ 12.500"). */
  lines: string[];
  ariaLabel: string;
}

/** Una referencia dibujada (barra, puerta…). `key` la identifica aunque todavía no tenga id. */
export interface CanvasItem extends FloorBox {
  key: string;
  kind: FloorItemKind;
  label: string | null;
}

export interface BoxChange {
  key: string;
  box: FloorBox;
}

interface Drag {
  key: string;
  mode: 'move' | 'resize';
  keepSquare: boolean;
  start: FloorBox;
  startX: number;
  startY: number;
  pxPerUnit: number;
  moved: boolean;
  pointerId: number;
}

const SNAP = 0.5;
const DRAG_THRESHOLD_PX = 5;

/**
 * El plano de una zona del salón (V18), dibujado en porcentajes sobre un
 * lienzo 4:3. Lo usan el editor de Mesas (`editable`: arrastrar, agrandar
 * con la esquina, flechas del teclado) y el mozo al elegir la mesa (solo
 * tocar).
 *
 * Las claves son `t:<id de mesa>` para las mesas e `i:<key>` para las
 * referencias. El componente no guarda nada: avisa con `pick` qué se tocó
 * y con `boxChange` a dónde se movió, y la pantalla actualiza sus datos.
 */
@Component({
  selector: 'app-floor-canvas',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './floor-canvas.component.html',
  styleUrl: './floor-canvas.component.css',
})
export class FloorCanvasComponent {
  readonly tables = input<CanvasTable[]>([]);
  readonly items = input<CanvasItem[]>([]);
  readonly editable = input(false);
  readonly selected = input<string | null>(null);

  /** Se tocó una mesa/referencia (sin arrastrar), o el fondo (`null`). */
  readonly pick = output<string | null>();
  readonly boxChange = output<BoxChange>();

  private readonly canvas = viewChild.required<ElementRef<HTMLDivElement>>('canvas');
  private drag: Drag | null = null;
  /** Después de soltar, el navegador manda un click al lienzo: no tiene que deseleccionar. */
  private ignoreClickUntil = 0;

  readonly W = FLOOR_WIDTH;
  readonly H = FLOOR_HEIGHT;
  readonly itemLabel = floorItemLabel;

  left(box: FloorBox): string {
    return (box.x / FLOOR_WIDTH) * 100 + '%';
  }
  top(box: FloorBox): string {
    return (box.y / FLOOR_HEIGHT) * 100 + '%';
  }
  width(box: FloorBox): string {
    return (box.width / FLOOR_WIDTH) * 100 + '%';
  }
  height(box: FloorBox): string {
    return (box.height / FLOOR_HEIGHT) * 100 + '%';
  }
  side(box: FloorBox): number {
    return Math.min(box.width, box.height);
  }

  // ---------------------------------------------------------------------
  // Arrastrar
  // ---------------------------------------------------------------------

  down(event: PointerEvent, key: string, box: FloorBox, mode: 'move' | 'resize', keepSquare = false): void {
    if (event.button !== 0) return;
    event.stopPropagation();
    if (!this.editable()) return; // en modo "ver", el click normal ya avisa
    event.preventDefault();
    const el = this.canvas().nativeElement;
    el.setPointerCapture(event.pointerId);
    this.drag = {
      key,
      mode,
      keepSquare,
      start: { ...box },
      startX: event.clientX,
      startY: event.clientY,
      pxPerUnit: el.getBoundingClientRect().width / FLOOR_WIDTH,
      moved: false,
      pointerId: event.pointerId,
    };
    if (mode === 'move' && this.selected() !== key) this.pick.emit(key);
  }

  move(event: PointerEvent): void {
    const drag = this.drag;
    if (!drag || event.pointerId !== drag.pointerId) return;
    const dxPx = event.clientX - drag.startX;
    const dyPx = event.clientY - drag.startY;
    if (!drag.moved && Math.hypot(dxPx, dyPx) < DRAG_THRESHOLD_PX) return;
    drag.moved = true;
    const du = dxPx / drag.pxPerUnit;
    const dv = dyPx / drag.pxPerUnit;
    const s = drag.start;
    let box: FloorBox;
    if (drag.mode === 'move') {
      box = {
        ...s,
        x: clamp(snap(s.x + du), 0, FLOOR_WIDTH - s.width),
        y: clamp(snap(s.y + dv), 0, FLOOR_HEIGHT - s.height),
      };
    } else if (drag.keepSquare) {
      const grow = Math.abs(du) > Math.abs(dv) ? du : dv;
      const side = clamp(snap(s.width + grow), FLOOR_MIN_SIZE, Math.min(FLOOR_WIDTH - s.x, FLOOR_HEIGHT - s.y));
      box = { ...s, width: side, height: side };
    } else {
      box = {
        ...s,
        width: clamp(snap(s.width + du), FLOOR_MIN_SIZE / 2, FLOOR_WIDTH - s.x),
        height: clamp(snap(s.height + dv), FLOOR_MIN_SIZE / 2, FLOOR_HEIGHT - s.y),
      };
    }
    this.boxChange.emit({ key: drag.key, box });
  }

  up(event: PointerEvent): void {
    const drag = this.drag;
    if (!drag || event.pointerId !== drag.pointerId) return;
    this.drag = null;
    this.ignoreClickUntil = performance.now() + 350;
    const el = this.canvas().nativeElement;
    if (el.hasPointerCapture(event.pointerId)) el.releasePointerCapture(event.pointerId);
  }

  /** Click en una mesa o referencia (en modo "ver", o con el teclado). */
  click(event: MouseEvent, key: string): void {
    event.stopPropagation();
    if (!this.editable()) this.pick.emit(key);
    else if (event.detail === 0) this.pick.emit(key); // Enter/Espacio con el teclado
  }

  background(): void {
    if (!this.drag && performance.now() > this.ignoreClickUntil) this.pick.emit(null);
  }

  /** Flechas: mueven lo elegido 1 unidad (5 con Shift). */
  key(event: KeyboardEvent, key: string, box: FloorBox): void {
    if (!this.editable()) return;
    const step = event.shiftKey ? 5 : 1;
    const delta: Record<string, [number, number]> = {
      ArrowLeft: [-step, 0],
      ArrowRight: [step, 0],
      ArrowUp: [0, -step],
      ArrowDown: [0, step],
    };
    const d = delta[event.key];
    if (!d) return;
    event.preventDefault();
    if (this.selected() !== key) this.pick.emit(key);
    this.boxChange.emit({
      key,
      box: {
        ...box,
        x: clamp(box.x + d[0], 0, FLOOR_WIDTH - box.width),
        y: clamp(box.y + d[1], 0, FLOOR_HEIGHT - box.height),
      },
    });
  }
}

function snap(value: number): number {
  return Math.round(value / SNAP) * SNAP;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), Math.max(min, max));
}
