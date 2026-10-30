import { Component, OnInit, computed, inject, signal, viewChild, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TABLE_STATUS_LABELS, TableResponse } from '../../core/models/table.model';
import { AuthService } from '../../core/services/auth.service';
import { extractErrorMessage } from '../../core/services/error.util';
import { LocationResolverService } from '../../core/services/location-resolver.service';
import { TableService } from '../../core/services/table.service';
import { ToastService } from '../../core/services/toast.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { ModalComponent } from '../../shared/components/modal/modal.component';
import { FloorEditorComponent } from './floor-editor.component';

type CreateMode = 'many' | 'one';
type View = 'plan' | 'list';

const VIEW_KEY = 'coffe.tablesView';

/**
 * Mesas del local (pantalla nueva: antes solo se cargaban por API). Lo más
 * rápido para arrancar es "varias de una vez" (de la 1 a la 12, por
 * ejemplo); después se puede ponerle nombre a cada una ("Ventana",
 * "Vereda") y la cantidad de lugares.
 *
 * Alta/edición/baja: OWNER/ADMIN — mismo @PreAuthorize que TableController.
 * SUPERVISOR la ve de solo lectura.
 */
@Component({
    selector: 'app-table-list',
    imports: [FormsModule, IconComponent, ModalComponent, EmptyStateComponent, FloorEditorComponent],
    templateUrl: './table-list.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './table-list.component.css'
})
export class TableListComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly locationResolver = inject(LocationResolverService);
  private readonly tableService = inject(TableService);
  private readonly toast = inject(ToastService);

  readonly statusLabels = TABLE_STATUS_LABELS;
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly tables = signal<TableResponse[]>([]);
  readonly showInactive = signal(false);
  private locationId: string | null = null;
  readonly location = signal<string | null>(null);

  /** V18 — "Plano" (el salón dibujado) o "Lista" (las tarjetas de siempre). Se recuerda en este navegador. */
  readonly view = signal<View>(readView());
  private readonly floorEditor = viewChild(FloorEditorComponent);

  readonly canManage = computed(() => {
    const role = this.authService.user()?.role;
    return role === 'OWNER' || role === 'ADMIN';
  });

  readonly visibleTables = computed(() =>
    this.tables()
      .filter((table) => this.showInactive() || table.isActive)
      .sort((a, b) => a.number - b.number),
  );
  readonly inactiveCount = computed(() => this.tables().filter((table) => !table.isActive).length);
  readonly nextNumber = computed(() => Math.max(0, ...this.tables().map((table) => table.number)) + 1);

  // ---- Alta ----
  readonly showCreate = signal(false);
  readonly createMode = signal<CreateMode>('many');
  readonly fromNumber = signal(1);
  readonly toNumber = signal(10);
  readonly newNumber = signal(1);
  readonly newLabel = signal('');
  readonly newCapacity = signal<number | null>(4);
  readonly saving = signal(false);
  readonly formError = signal<string | null>(null);

  readonly manyCount = computed(() => Math.max(0, this.toNumber() - this.fromNumber() + 1));

  // ---- Edición ----
  readonly editing = signal<TableResponse | null>(null);
  readonly editNumber = signal(1);
  readonly editLabel = signal('');
  readonly editCapacity = signal<number | null>(null);

  async ngOnInit(): Promise<void> {
    try {
      this.locationId = await this.locationResolver.resolve();
      this.location.set(this.locationId);
      await this.reload();
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudieron cargar las mesas.'));
    } finally {
      this.loading.set(false);
    }
  }

  async reload(): Promise<void> {
    if (!this.locationId) return;
    this.tables.set(await this.tableService.findAllByLocation(this.locationId));
    void this.floorEditor()?.refresh();
  }

  setView(view: View): void {
    this.view.set(view);
    try {
      localStorage.setItem(VIEW_KEY, view);
    } catch {
      /* sin almacenamiento: se recuerda solo mientras está abierta */
    }
  }

  openCreate(): void {
    const next = this.nextNumber();
    this.createMode.set(this.tables().length === 0 ? 'many' : 'one');
    this.fromNumber.set(next);
    this.toNumber.set(next + 9);
    this.newNumber.set(next);
    this.newLabel.set('');
    this.newCapacity.set(4);
    this.formError.set(null);
    this.showCreate.set(true);
  }

  async create(): Promise<void> {
    const locationId = this.locationId;
    if (!locationId || this.saving()) return;
    this.formError.set(null);

    const capacity = this.newCapacity() && this.newCapacity()! > 0 ? this.newCapacity() : null;
    let numbers: number[];
    if (this.createMode() === 'many') {
      if (this.fromNumber() < 1 || this.toNumber() < this.fromNumber()) {
        this.formError.set('Revisá los números: "hasta" tiene que ser mayor o igual que "desde".');
        return;
      }
      if (this.manyCount() > 60) {
        this.formError.set('Como mucho 60 mesas de una vez.');
        return;
      }
      numbers = Array.from({ length: this.manyCount() }, (_, i) => this.fromNumber() + i);
    } else {
      if (this.newNumber() < 1) {
        this.formError.set('El número de mesa tiene que ser 1 o más.');
        return;
      }
      numbers = [this.newNumber()];
    }

    const existing = new Set(this.tables().map((table) => table.number));
    const skipped = numbers.filter((n) => existing.has(n));
    const toCreate = numbers.filter((n) => !existing.has(n));
    if (toCreate.length === 0) {
      this.formError.set(skipped.length === 1 ? `La mesa ${skipped[0]} ya existe.` : 'Esas mesas ya existen.');
      return;
    }

    this.saving.set(true);
    try {
      for (const number of toCreate) {
        await this.tableService.create({
          locationId,
          number,
          label: this.createMode() === 'one' ? this.newLabel().trim() || null : null,
          capacity,
        });
      }
      await this.reload();
      this.showCreate.set(false);
      this.toast.success(
        toCreate.length === 1 ? `Mesa ${toCreate[0]} creada.` : `${toCreate.length} mesas creadas.` +
          (skipped.length ? ` (${skipped.length} ya existían)` : ''),
      );
    } catch (error) {
      await this.reload();
      this.formError.set(extractErrorMessage(error, 'No se pudieron crear las mesas.'));
    } finally {
      this.saving.set(false);
    }
  }

  startEdit(table: TableResponse): void {
    if (!this.canManage()) return;
    this.editing.set(table);
    this.editNumber.set(table.number);
    this.editLabel.set(table.label ?? '');
    this.editCapacity.set(table.capacity);
    this.formError.set(null);
  }

  async saveEdit(): Promise<void> {
    const table = this.editing();
    if (!table || this.saving()) return;
    this.saving.set(true);
    this.formError.set(null);
    try {
      const updated = await this.tableService.update(table.id, {
        number: this.editNumber(),
        label: this.editLabel().trim() || null,
        capacity: this.editCapacity() && this.editCapacity()! > 0 ? this.editCapacity() : null,
      });
      this.tables.set(this.tables().map((t) => (t.id === updated.id ? updated : t)));
      void this.floorEditor()?.refresh();
      this.editing.set(null);
      this.toast.success('Mesa actualizada.');
    } catch (error) {
      this.formError.set(extractErrorMessage(error, 'No se pudo guardar la mesa.'));
    } finally {
      this.saving.set(false);
    }
  }

  async toggleActive(table: TableResponse): Promise<void> {
    if (this.saving()) return;
    this.saving.set(true);
    try {
      if (table.isActive) {
        await this.tableService.deactivate(table.id);
        this.toast.info(`Mesa ${table.number} dada de baja.`);
      } else {
        await this.tableService.update(table.id, { isActive: true });
        this.toast.success(`Mesa ${table.number} reactivada.`);
      }
      this.editing.set(null);
      await this.reload();
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo cambiar la mesa.'));
    } finally {
      this.saving.set(false);
    }
  }
}

function readView(): View {
  try {
    return localStorage.getItem(VIEW_KEY) === 'list' ? 'list' : 'plan';
  } catch {
    return 'plan';
  }
}
