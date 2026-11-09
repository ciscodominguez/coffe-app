import { Component, OnDestroy, OnInit, computed, inject, input, output, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  FLOOR_HEIGHT,
  FLOOR_ITEM_KINDS,
  FLOOR_MIN_SIZE,
  FLOOR_WIDTH,
  FloorArea,
  FloorBox,
  FloorItemKind,
  FloorPlan,
  TABLE_SHAPE_LABELS,
  floorItemLabel,
  isPlacedIn,
} from '../../core/models/floor.model';
import { TableResponse, TableShape } from '../../core/models/table.model';
import { extractErrorMessage } from '../../core/services/error.util';
import { FloorService } from '../../core/services/floor.service';
import { ToastService } from '../../core/services/toast.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { BoxChange, CanvasItem, CanvasTable, FloorCanvasComponent } from '../../shared/components/floor-canvas/floor-canvas.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { ModalComponent } from '../../shared/components/modal/modal.component';

/** Una referencia mientras se edita (las nuevas todavía no tienen id). */
interface WorkItem extends FloorBox {
  key: string;
  id: string | null;
  areaId: string;
  kind: FloorItemKind;
  label: string | null;
}

const SAVE_DELAY_MS = 700;
const AREA_SUGGESTIONS = ['Salón', 'Vereda', 'Planta alta', 'Patio', 'Terraza', 'Barra'];

/**
 * Plano del salón (V18), en la pantalla Mesas. El dueño y el administrador
 * acomodan las mesas arrastrándolas; se guarda solo (unos instantes después
 * de cada cambio). El supervisor lo ve, sin poder cambiarlo.
 */
@Component({
    selector: 'app-floor-editor',
    imports: [FormsModule, FloorCanvasComponent, IconComponent, ModalComponent, EmptyStateComponent],
    templateUrl: './floor-editor.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './floor-editor.component.css'
})
export class FloorEditorComponent implements OnInit, OnDestroy {
  private readonly floorService = inject(FloorService);
  private readonly toast = inject(ToastService);

  readonly locationId = input.required<string>();
  readonly canManage = input(false);
  /** "Editar nombre y lugares" de una mesa: lo resuelve la pantalla Mesas con su ventana. */
  readonly editTable = output<TableResponse>();
  /** Si el plano no carga, el usuario puede volver a la lista de siempre. */
  readonly showList = output<void>();

  readonly kinds = FLOOR_ITEM_KINDS;
  readonly shapeLabels = TABLE_SHAPE_LABELS;
  readonly shapes: TableShape[] = ['ROUND', 'SQUARE', 'RECT'];
  readonly suggestions = AREA_SUGGESTIONS;
  readonly itemLabel = floorItemLabel;

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly areas = signal<FloorArea[]>([]);
  readonly tables = signal<TableResponse[]>([]);
  readonly items = signal<WorkItem[]>([]);
  readonly areaId = signal<string | null>(null);
  readonly selected = signal<string | null>(null);
  readonly saveState = signal<'idle' | 'pending' | 'saving' | 'saved' | 'error'>('idle');

  private readonly dirtyAreas = new Set<string>();
  private saveTimer: ReturnType<typeof setTimeout> | null = null;
  private version = 0;
  private savingPromise: Promise<void> | null = null;
  private newKeySeq = 0;

  // ---- Zonas (crear / renombrar / borrar) ----
  readonly areaModal = signal<'create' | 'rename' | null>(null);
  readonly areaName = signal('');
  readonly areaError = signal<string | null>(null);
  readonly areaBusy = signal(false);
  readonly confirmDelete = signal(false);

  readonly currentArea = computed(() => this.areas().find((a) => a.id === this.areaId()) ?? null);

  readonly unplaced = computed(() =>
    this.tables()
      .filter((t) => !t.areaId || t.x === null)
      .sort((a, b) => a.number - b.number),
  );

  readonly canvasTables = computed<CanvasTable[]>(() => {
    const areaId = this.areaId();
    if (!areaId) return [];
    const manage = this.canManage();
    return this.tables()
      .filter((t) => isPlacedIn(t, areaId))
      .map((t) => ({
        id: t.id,
        number: t.number,
        label: t.label,
        shape: t.shape,
        x: t.x!,
        y: t.y!,
        width: t.width!,
        height: t.height!,
        tone: manage ? 'neutral' : t.status === 'OCCUPIED' ? 'busy' : t.status === 'RESERVED' ? 'reserved' : 'free',
        lines: t.label ? [t.label] : t.capacity ? [t.capacity + ' lug.'] : [],
        ariaLabel: `Mesa ${t.number}${t.label ? ' (' + t.label + ')' : ''}`,
      }));
  });

  readonly canvasItems = computed<CanvasItem[]>(() =>
    this.items()
      .filter((i) => i.areaId === this.areaId())
      .map((i) => ({ key: i.key, kind: i.kind, label: i.label, x: i.x, y: i.y, width: i.width, height: i.height })),
  );

  readonly selectedTable = computed(() => {
    const key = this.selected();
    return key?.startsWith('t:') ? (this.tables().find((t) => t.id === key.slice(2)) ?? null) : null;
  });

  readonly selectedItem = computed(() => {
    const key = this.selected();
    return key?.startsWith('i:') ? (this.items().find((i) => i.key === key.slice(2)) ?? null) : null;
  });

  async ngOnInit(): Promise<void> {
    await this.load();
  }

  ngOnDestroy(): void {
    // Si quedó algo sin guardar (se fue de la pantalla enseguida), se guarda igual.
    if (this.saveTimer) {
      clearTimeout(this.saveTimer);
      void this.flush();
    }
  }

  /** Lo llama la pantalla Mesas después de cambiar una mesa (nombre, baja…). */
  async refresh(): Promise<void> {
    await this.flush();
    await this.load();
  }

  retry(): void {
    void this.load();
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    this.loadError.set(null);
    try {
      this.apply(await this.floorService.plan(this.locationId()));
    } catch (error) {
      this.loadError.set(extractErrorMessage(error, 'No se pudo cargar el plano.'));
    } finally {
      this.loading.set(false);
    }
  }

  private apply(plan: FloorPlan): void {
    this.areas.set(plan.areas);
    this.tables.set(plan.tables.map((t) => ({ ...t, ...num(t) })));
    this.items.set(
      plan.items.map((i) => ({
        key: i.id!,
        id: i.id,
        areaId: i.areaId,
        kind: i.kind,
        label: i.label,
        x: +i.x,
        y: +i.y,
        width: +i.width,
        height: +i.height,
      })),
    );
    if (!plan.areas.some((a) => a.id === this.areaId())) this.areaId.set(plan.areas[0]?.id ?? null);
    const sel = this.selected();
    if (sel && !(this.selectedTable() && isPlacedIn(this.selectedTable()!, this.areaId() ?? '')) && !this.selectedItem()) {
      this.selected.set(null);
    }
  }

  // ---------------------------------------------------------------------
  // Zonas
  // ---------------------------------------------------------------------

  async chooseArea(id: string): Promise<void> {
    this.selected.set(null);
    this.areaId.set(id);
  }

  openCreateArea(): void {
    const used = new Set(this.areas().map((a) => a.name.toLowerCase()));
    this.areaName.set(this.areas().length === 0 ? 'Salón' : (AREA_SUGGESTIONS.find((s) => !used.has(s.toLowerCase())) ?? ''));
    this.areaError.set(null);
    this.areaModal.set('create');
  }

  openRenameArea(): void {
    const area = this.currentArea();
    if (!area) return;
    this.areaName.set(area.name);
    this.areaError.set(null);
    this.areaModal.set('rename');
  }

  async saveArea(): Promise<void> {
    const name = this.areaName().trim();
    if (!name) {
      this.areaError.set('Ponele un nombre a la zona.');
      return;
    }
    if (this.areaBusy()) return;
    this.areaBusy.set(true);
    this.areaError.set(null);
    try {
      if (this.areaModal() === 'create') {
        const first = this.areas().length === 0;
        const area = await this.floorService.createArea(this.locationId(), name);
        this.areas.set([...this.areas(), area]);
        this.areaId.set(area.id);
        this.selected.set(null);
        // La primera zona arranca con todas las mesas acomodadas en filas.
        if (first && this.unplaced().length > 0) this.placeAll();
        this.toast.success(first ? 'Listo: acomodá las mesas como están en tu café.' : `Zona "${area.name}" creada.`);
      } else {
        const area = await this.floorService.renameArea(this.areaId()!, name);
        this.areas.set(this.areas().map((a) => (a.id === area.id ? area : a)));
      }
      this.areaModal.set(null);
    } catch (error) {
      this.areaError.set(extractErrorMessage(error, 'No se pudo guardar la zona.'));
    } finally {
      this.areaBusy.set(false);
    }
  }

  async deleteArea(): Promise<void> {
    const area = this.currentArea();
    if (!area || this.areaBusy()) return;
    this.areaBusy.set(true);
    try {
      await this.flush();
      await this.floorService.deleteArea(area.id);
      this.dirtyAreas.delete(area.id);
      this.confirmDelete.set(false);
      this.areaId.set(null);
      await this.load();
      this.toast.info(`Zona "${area.name}" borrada. Sus mesas quedaron sin ubicar.`);
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo borrar la zona.'));
    } finally {
      this.areaBusy.set(false);
    }
  }

  // ---------------------------------------------------------------------
  // Mesas y referencias
  // ---------------------------------------------------------------------

  onPick(key: string | null): void {
    this.selected.set(key);
  }

  onBox(change: BoxChange): void {
    if (!this.canManage()) return;
    this.setBox(change.key, change.box);
  }

  /** Pone una mesa "sin ubicar" en un lugar libre de la zona actual. */
  place(table: TableResponse): void {
    const areaId = this.areaId();
    if (!areaId || !this.canManage()) return;
    const w = table.shape === 'RECT' ? 16 : 10;
    const spot = this.freeSpot(w, 10);
    this.updateTable(table.id, { areaId, x: spot.x, y: spot.y, width: w, height: 10 });
    this.selected.set('t:' + table.id);
  }

  /** Acomoda todas las mesas sin ubicar, en filas. */
  placeAll(): void {
    const areaId = this.areaId();
    if (!areaId) return;
    for (const table of this.unplaced()) {
      const w = table.shape === 'RECT' ? 16 : 10;
      const spot = this.freeSpot(w, 10);
      this.updateTable(table.id, { areaId, x: spot.x, y: spot.y, width: w, height: 10 });
    }
    this.selected.set(null);
  }

  unplace(table: TableResponse): void {
    this.updateTable(table.id, { areaId: null, x: null, y: null, width: null, height: null });
    this.selected.set(null);
  }

  setShape(table: TableResponse, shape: TableShape): void {
    let { x, y, width, height } = { x: table.x!, y: table.y!, width: table.width!, height: table.height! };
    if (shape === 'RECT' && width === height) width = Math.min(width * 1.6, FLOOR_WIDTH - x);
    if (shape !== 'RECT') {
      const side = Math.min(Math.max(width, height), FLOOR_WIDTH - x, FLOOR_HEIGHT - y);
      width = height = side;
    }
    this.updateTable(table.id, { shape, x, y, width: round(width), height: round(height) });
  }

  /** Agranda o achica lo elegido (mesa o referencia), sin salirse del plano. */
  resize(step: number): void {
    const key = this.selected();
    const box = this.boxOf(key);
    if (!key || !box) return;
    const keepSquare = !!this.selectedTable() && this.selectedTable()!.shape !== 'RECT';
    let width = clampSize(box.width + step, FLOOR_WIDTH - box.x);
    let height = clampSize(box.height + step, FLOOR_HEIGHT - box.y);
    if (keepSquare) width = height = Math.min(width, height);
    if (!!this.selectedItem() && (box.height <= 3 || box.width <= 3)) {
      // Paredes y ventanas: se alargan, no se engordan.
      if (box.height <= box.width) height = box.height;
      else width = box.width;
    }
    this.setBox(key, { ...box, width, height });
  }

  /** Gira (intercambia ancho y alto): para mesas largas, paredes, puertas… */
  rotate(): void {
    const key = this.selected();
    const box = this.boxOf(key);
    if (!key || !box) return;
    const width = Math.min(box.height, FLOOR_WIDTH - box.x);
    const height = Math.min(box.width, FLOOR_HEIGHT - box.y);
    this.setBox(key, { ...box, width, height });
  }

  addItem(kind: FloorItemKind): void {
    const areaId = this.areaId();
    if (!areaId) return;
    const def = FLOOR_ITEM_KINDS.find((k) => k.kind === kind)!;
    const spot = this.freeSpot(def.w, def.h);
    const key = 'new-' + ++this.newKeySeq;
    this.items.set([...this.items(), { key, id: null, areaId, kind, label: null, x: spot.x, y: spot.y, width: def.w, height: def.h }]);
    this.selected.set('i:' + key);
    this.changed(areaId);
  }

  setItemLabel(item: WorkItem, label: string): void {
    this.items.set(this.items().map((i) => (i.key === item.key ? { ...i, label: label.slice(0, 40) || null } : i)));
    this.changed(item.areaId);
  }

  removeItem(item: WorkItem): void {
    this.items.set(this.items().filter((i) => i.key !== item.key));
    this.selected.set(null);
    this.changed(item.areaId);
  }

  // ---------------------------------------------------------------------
  // Guardado automático
  // ---------------------------------------------------------------------

  private changed(...areaIds: (string | null | undefined)[]): void {
    for (const id of areaIds) if (id) this.dirtyAreas.add(id);
    this.version++;
    this.saveState.set('pending');
    if (this.saveTimer) clearTimeout(this.saveTimer);
    this.saveTimer = setTimeout(() => void this.flush(), SAVE_DELAY_MS);
  }

  /** Guarda ya lo que esté pendiente (espera a que termine si ya estaba guardando). */
  async flush(): Promise<void> {
    if (this.saveTimer) {
      clearTimeout(this.saveTimer);
      this.saveTimer = null;
    }
    while (this.savingPromise) await this.savingPromise;
    if (this.dirtyAreas.size === 0) return;
    this.savingPromise = this.saveDirty();
    try {
      await this.savingPromise;
    } finally {
      this.savingPromise = null;
    }
  }

  private async saveDirty(): Promise<void> {
    const areas = [...this.dirtyAreas].filter((id) => this.areas().some((a) => a.id === id));
    this.dirtyAreas.clear();
    const startVersion = this.version;
    this.saveState.set('saving');
    try {
      let plan: FloorPlan | null = null;
      for (const areaId of areas) {
        plan = await this.floorService.saveLayout(areaId, {
          tables: this.tables()
            .filter((t) => isPlacedIn(t, areaId))
            .map((t) => ({ id: t.id, x: t.x!, y: t.y!, width: t.width!, height: t.height!, shape: t.shape })),
          items: this.items()
            .filter((i) => i.areaId === areaId)
            .map((i) => ({ id: i.id, kind: i.kind, label: i.label, x: i.x, y: i.y, width: i.width, height: i.height })),
        });
      }
      // Si mientras guardaba no cambió nada más, se toma lo del servidor (así las referencias nuevas reciben su id).
      if (plan && this.version === startVersion) {
        const sel = this.selectedItem();
        this.apply(plan);
        if (sel && !sel.id) {
          const match = this.items().find(
            (i) => i.areaId === sel.areaId && i.kind === sel.kind && i.x === sel.x && i.y === sel.y,
          );
          this.selected.set(match ? 'i:' + match.key : null);
        }
      }
      this.saveState.set(this.dirtyAreas.size > 0 ? 'pending' : 'saved');
    } catch (error) {
      this.saveState.set('error');
      this.toast.error(extractErrorMessage(error, 'No se pudo guardar el plano.'));
      await this.load();
    }
  }

  // ---------------------------------------------------------------------

  private boxOf(key: string | null): FloorBox | null {
    if (!key) return null;
    if (key.startsWith('t:')) {
      const t = this.tables().find((x) => x.id === key.slice(2));
      return t && t.x !== null ? { x: t.x, y: t.y!, width: t.width!, height: t.height! } : null;
    }
    const i = this.items().find((x) => x.key === key.slice(2));
    return i ? { x: i.x, y: i.y, width: i.width, height: i.height } : null;
  }

  private setBox(key: string, box: FloorBox): void {
    if (key.startsWith('t:')) {
      this.updateTable(key.slice(2), { x: box.x, y: box.y, width: box.width, height: box.height });
    } else {
      const itemKey = key.slice(2);
      const item = this.items().find((i) => i.key === itemKey);
      if (!item) return;
      this.items.set(this.items().map((i) => (i.key === itemKey ? { ...i, ...box } : i)));
      this.changed(item.areaId);
    }
  }

  private updateTable(id: string, patch: Partial<TableResponse>): void {
    const before = this.tables().find((t) => t.id === id);
    if (!before) return;
    this.tables.set(this.tables().map((t) => (t.id === id ? { ...t, ...patch } : t)));
    this.changed(before.areaId, patch.areaId === undefined ? before.areaId : patch.areaId);
  }

  /** Primer lugar libre (de arriba a la izquierda) donde entra una caja de w x h. */
  private freeSpot(w: number, h: number): { x: number; y: number } {
    const areaId = this.areaId();
    const boxes: FloorBox[] = [
      ...this.tables()
        .filter((t) => areaId && isPlacedIn(t, areaId))
        .map((t) => ({ x: t.x!, y: t.y!, width: t.width!, height: t.height! })),
      ...this.items().filter((i) => i.areaId === areaId),
    ];
    const gap = 3;
    for (let y = gap; y + h <= FLOOR_HEIGHT; y += 2) {
      for (let x = gap; x + w <= FLOOR_WIDTH; x += 2) {
        const hit = boxes.some(
          (b) => x < b.x + b.width + gap && x + w + gap > b.x && y < b.y + b.height + gap && y + h + gap > b.y,
        );
        if (!hit) return { x, y };
      }
    }
    return { x: 0, y: 0 };
  }
}

function num(t: TableResponse): Partial<TableResponse> {
  return {
    x: t.x === null ? null : +t.x,
    y: t.y === null ? null : +t.y,
    width: t.width === null ? null : +t.width,
    height: t.height === null ? null : +t.height,
  };
}

function round(value: number): number {
  return Math.round(value * 2) / 2;
}

function clampSize(value: number, max: number): number {
  return Math.max(FLOOR_MIN_SIZE / 2, Math.min(round(value), max));
}
