import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { LocationService } from '../../core/services/business.service';
import {
  IngredientResponse,
  IngredientStockLevelResponse,
  ProductStockLevelResponse,
  StockAdjustmentResponse,
} from '../../core/models/stock.model';
import { StockService } from '../../core/services/stock.service';
import { extractErrorMessage } from '../../core/services/error.util';
import { ToastService } from '../../core/services/toast.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { ModalComponent } from '../../shared/components/modal/modal.component';

/**
 * Pantalla "Insumos y Stock" — Bloque D de las mejoras pedidas (ítems
 * 12/13, "manejo de stock"). Abierta a TODOS los roles autenticados
 * (WAITER/KITCHEN incluidos — ver app.routes.ts, sin roleGuard a
 * propósito) para que cualquiera pueda consultar qué se está por acabar;
 * el alta/edición de insumos y los ajustes manuales quedan reservados a
 * gerencia (ver {@link canManage}) — "gerencia carga, todos consultan",
 * tal cual lo pidió Fran.
 *
 * <p>Tres secciones:
 * <ol>
 *   <li>Catálogo de insumos (alta/edición/baja lógica) — gerencia.
 *   <li>Stock de insumos en el local actual, con la alerta de "stock bajo"
 *       ya calculada por el backend (nunca traba nada, ver el Javadoc de
 *       {@code IngredientStock} del backend) y ajuste manual con
 *       auditoría + historial.
 *   <li>Stock de productos en modo {@code DIRECT} en el local actual —
 *       mismo patrón que insumos. La receta de un producto en modo
 *       {@code RECIPE} se configura desde la pantalla de Productos, no
 *       acá (ver {@code ProductListComponent}).
 * </ol>
 *
 * <p>El panel de ajuste y el de historial son mutuamente excluyentes y
 * compartidos entre insumos/productos (un solo signal por tipo de panel,
 * gateado por id) — evita duplicar la lógica de "conteo real → delta
 * calculado por el backend" (mismo criterio que el conteo de efectivo de
 * la Caja).
 */
@Component({
    selector: 'app-ingredient-list',
    imports: [CommonModule, FormsModule, ReactiveFormsModule, IconComponent, ModalComponent, EmptyStateComponent],
    templateUrl: './ingredient-list.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './ingredient-list.component.css'
})
export class IngredientListComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly locationService = inject(LocationService);
  private readonly stockService = inject(StockService);
  private readonly toast = inject(ToastService);

  /** Pestañas: el stock de hoy (lo que más se mira) o el catálogo de insumos (lo que se carga una vez). */
  readonly tab = signal<'levels' | 'catalog'>('levels');
  readonly onlyLow = signal(false);
  /** Unidades típicas — un toque y listo. */
  readonly unitSuggestions: readonly string[] = ['g', 'kg', 'ml', 'l', 'unidad'];

  readonly lowCount = computed(
    () =>
      this.ingredientLevels().filter((level) => level.belowMinimum).length +
      this.productLevels().filter((level) => level.belowMinimum).length,
  );
  readonly visibleIngredientLevels = computed(() =>
    [...this.ingredientLevels()]
      .filter((level) => !this.onlyLow() || level.belowMinimum)
      .sort((a, b) => Number(b.belowMinimum) - Number(a.belowMinimum) || a.ingredientName.localeCompare(b.ingredientName)),
  );
  readonly visibleProductLevels = computed(() =>
    [...this.productLevels()]
      .filter((level) => !this.onlyLow() || level.belowMinimum)
      .sort((a, b) => Number(b.belowMinimum) - Number(a.belowMinimum) || a.productName.localeCompare(b.productName)),
  );


  private resolvedLocationId: string | null = null;

  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  readonly ingredients = signal<IngredientResponse[]>([]);
  readonly ingredientLevels = signal<IngredientStockLevelResponse[]>([]);
  readonly productLevels = signal<ProductStockLevelResponse[]>([]);

  readonly editingId = signal<string | null>(null);
  readonly saving = signal(false);
  readonly creating = signal(false);
  readonly showCreateForm = signal(false);

  /** Alta/edición de insumos y ajustes manuales exigen gerencia — ver StockController del backend. */
  readonly canManage = computed(() => {
    const role = this.authService.user()?.role;
    return role === 'OWNER' || role === 'ADMIN' || role === 'SUPERVISOR';
  });

  readonly sortedIngredients = computed(() => [...this.ingredients()].sort((a, b) => a.name.localeCompare(b.name)));

  readonly createForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(150)]],
    unit: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(20)]],
    minStock: this.fb.control<number | null>(null),
  });

  readonly editForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(150)]],
    unit: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(20)]],
    minStock: this.fb.control<number | null>(null),
  });

  // ---- Ajuste manual — panel único, gateado por id de insumo o de producto ----
  readonly adjustingIngredientId = signal<string | null>(null);
  readonly adjustingProductId = signal<string | null>(null);
  readonly adjustQuantity = signal<number>(0);
  readonly adjustReason = signal('');
  readonly adjusting = signal(false);
  readonly adjustError = signal<string | null>(null);

  // ---- Historial — mismo criterio de panel único ----
  readonly historyForIngredientId = signal<string | null>(null);
  readonly historyForProductId = signal<string | null>(null);
  readonly history = signal<StockAdjustmentResponse[]>([]);
  readonly historyLoading = signal(false);

  async ngOnInit(): Promise<void> {
    try {
      this.resolvedLocationId = await this.resolveLocationId();
      await this.reload();
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudo cargar la pantalla de insumos.'));
    } finally {
      this.loading.set(false);
    }
  }

  async reload(): Promise<void> {
    const locationId = this.resolvedLocationId;
    if (!locationId) return;
    this.errorMessage.set(null);
    try {
      const [ingredients, ingredientLevels, productLevels] = await Promise.all([
        this.stockService.findAllIngredients(),
        this.stockService.ingredientLevels(locationId),
        this.stockService.productLevels(locationId),
      ]);
      this.ingredients.set(ingredients);
      this.ingredientLevels.set(ingredientLevels);
      this.productLevels.set(productLevels);
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudieron cargar los insumos.'));
    }
  }

  toggleCreateForm(): void {
    this.showCreateForm.set(!this.showCreateForm());
    this.createForm.reset({ name: '', unit: '', minStock: null });
  }

  async createIngredient(): Promise<void> {
    if (this.createForm.invalid || this.creating()) return;
    this.creating.set(true);
    this.errorMessage.set(null);
    try {
      const raw = this.createForm.getRawValue();
      await this.stockService.createIngredient({ name: raw.name, unit: raw.unit, minStock: raw.minStock });
      this.showCreateForm.set(false);
      this.createForm.reset({ name: '', unit: '', minStock: null });
      await this.reload();
      this.toast.success(`Insumo "${raw.name}" creado. Cargá cuánto hay con "Ajustar".`);
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo crear el insumo.'));
    } finally {
      this.creating.set(false);
    }
  }

  startEdit(ingredient: IngredientResponse): void {
    this.editingId.set(ingredient.id);
    this.editForm.setValue({ name: ingredient.name, unit: ingredient.unit, minStock: ingredient.minStock });
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  async save(id: string): Promise<void> {
    if (this.editForm.invalid || this.saving()) return;
    this.saving.set(true);
    this.errorMessage.set(null);
    try {
      const raw = this.editForm.getRawValue();
      await this.stockService.updateIngredient(id, { name: raw.name, unit: raw.unit, minStock: raw.minStock });
      this.editingId.set(null);
      await this.reload();
      this.toast.success('Insumo guardado.');
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo guardar el insumo.'));
    } finally {
      this.saving.set(false);
    }
  }

  /** Baja lógica — sin DELETE físico, mismo criterio que Categorías/Productos (ver el Javadoc de {@code Ingredient} del backend: un insumo usado en una receta no puede desaparecer sin romperla). */
  async toggleActive(ingredient: IngredientResponse): Promise<void> {
    if (this.saving()) return;
    this.saving.set(true);
    this.errorMessage.set(null);
    try {
      await this.stockService.updateIngredient(ingredient.id, { isActive: !ingredient.isActive });
      this.editingId.set(null);
      await this.reload();
      this.toast.info(ingredient.isActive ? `"${ingredient.name}" dado de baja.` : `"${ingredient.name}" reactivado.`);
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo cambiar el estado del insumo.'));
    } finally {
      this.saving.set(false);
    }
  }

  /** Nombre y unidad de lo que se está ajustando / mirando el historial (títulos de las ventanas). */
  adjustTarget(): { name: string; unit: string; current: number } | null {
    const ingredientId = this.adjustingIngredientId();
    if (ingredientId) {
      const level = this.ingredientLevels().find((l) => l.ingredientId === ingredientId);
      return level ? { name: level.ingredientName, unit: level.unit, current: level.currentStock } : null;
    }
    const productId = this.adjustingProductId();
    if (productId) {
      const level = this.productLevels().find((l) => l.productId === productId);
      return level ? { name: level.productName, unit: 'unidades', current: level.currentStock } : null;
    }
    return null;
  }

  historyTitle(): string {
    const ingredientId = this.historyForIngredientId();
    if (ingredientId) return this.ingredientLevels().find((l) => l.ingredientId === ingredientId)?.ingredientName ?? '';
    const productId = this.historyForProductId();
    if (productId) return this.productLevels().find((l) => l.productId === productId)?.productName ?? '';
    return '';
  }

  editingIngredient(): IngredientResponse | null {
    return this.ingredients().find((ingredient) => ingredient.id === this.editingId()) ?? null;
  }

  /** Relleno de la barra de nivel: 100% = el doble del mínimo (sin mínimo, barra llena si hay stock). */
  levelPercent(current: number, min: number | null): number {
    if (current <= 0) return 0;
    if (!min || min <= 0) return 100;
    return Math.min(100, Math.round((current / (min * 2)) * 100));
  }

  // ---- Ajuste manual ----

  startAdjustIngredient(level: IngredientStockLevelResponse): void {
    this.closeHistory();
    this.adjustingProductId.set(null);
    this.adjustingIngredientId.set(level.ingredientId);
    this.adjustQuantity.set(level.currentStock);
    this.adjustReason.set('');
    this.adjustError.set(null);
  }

  startAdjustProduct(level: ProductStockLevelResponse): void {
    this.closeHistory();
    this.adjustingIngredientId.set(null);
    this.adjustingProductId.set(level.productId);
    this.adjustQuantity.set(level.currentStock);
    this.adjustReason.set('');
    this.adjustError.set(null);
  }

  cancelAdjust(): void {
    this.adjustingIngredientId.set(null);
    this.adjustingProductId.set(null);
  }

  /** Se manda la cantidad REAL contada (no un delta) — el backend calcula el delta, mismo criterio que el conteo de efectivo de la Caja. */
  async confirmAdjust(): Promise<void> {
    const locationId = this.resolvedLocationId;
    const ingredientId = this.adjustingIngredientId();
    const productId = this.adjustingProductId();
    if (this.adjusting() || !locationId || (!ingredientId && !productId)) return;
    if (this.adjustQuantity() < 0) {
      this.adjustError.set('La cantidad contada no puede ser negativa.');
      return;
    }
    this.adjusting.set(true);
    this.adjustError.set(null);
    try {
      const input = { locationId, newQuantity: this.adjustQuantity(), reason: this.adjustReason().trim() || null };
      if (ingredientId) {
        await this.stockService.adjustIngredientStock(ingredientId, input);
      } else if (productId) {
        await this.stockService.adjustProductStock(productId, input);
      }
      this.cancelAdjust();
      await this.reload();
      this.toast.success('Stock actualizado.');
    } catch (error) {
      this.adjustError.set(extractErrorMessage(error, 'No se pudo registrar el ajuste.'));
    } finally {
      this.adjusting.set(false);
    }
  }

  // ---- Historial ----

  async toggleHistoryIngredient(ingredientId: string): Promise<void> {
    if (this.historyForIngredientId() === ingredientId) {
      this.closeHistory();
      return;
    }
    this.cancelAdjust();
    this.historyForProductId.set(null);
    this.historyForIngredientId.set(ingredientId);
    await this.loadHistory(() => this.stockService.ingredientAdjustmentHistory(ingredientId, this.resolvedLocationId!));
  }

  async toggleHistoryProduct(productId: string): Promise<void> {
    if (this.historyForProductId() === productId) {
      this.closeHistory();
      return;
    }
    this.cancelAdjust();
    this.historyForIngredientId.set(null);
    this.historyForProductId.set(productId);
    await this.loadHistory(() => this.stockService.productAdjustmentHistory(productId, this.resolvedLocationId!));
  }

  closeHistory(): void {
    this.historyForIngredientId.set(null);
    this.historyForProductId.set(null);
    this.history.set([]);
  }

  private async loadHistory(run: () => Promise<StockAdjustmentResponse[]>): Promise<void> {
    this.historyLoading.set(true);
    this.errorMessage.set(null);
    try {
      this.history.set(await run());
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo cargar el historial de ajustes.'));
    } finally {
      this.historyLoading.set(false);
    }
  }

  /** Mismo criterio que CashSessionComponent/WaiterOrderComponent/KdsBoardComponent — ver el Javadoc de cualquiera de los tres. */
  private async resolveLocationId(): Promise<string> {
    const user = this.authService.user();
    if (user?.locationId) return user.locationId;

    const locations = await this.locationService.findAll();
    if (locations.length === 0) {
      throw new Error('Este negocio todavía no tiene ningún local cargado.');
    }
    return locations[0].id;
  }
}
