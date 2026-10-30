import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { CategoryResponse } from '../../core/models/category.model';
import { ProductResponse, STOCK_MODE_LABELS, StockMode } from '../../core/models/product.model';
import { IngredientResponse } from '../../core/models/stock.model';
import { OptionGroupResponse, groupRuleLabel } from '../../core/models/option.model';
import { OptionGroupService } from '../../core/services/option-group.service';
import { CategoryService, ProductService } from '../../core/services/catalog.service';
import { MediaService } from '../../core/services/media.service';
import { StockService } from '../../core/services/stock.service';
import { extractErrorMessage } from '../../core/services/error.util';
import { ToastService } from '../../core/services/toast.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { ModalComponent } from '../../shared/components/modal/modal.component';

/** Una línea de receta en edición — antes de guardar, ver {@link ProductListComponent#saveStockConfig}. */
interface RecipeRow {
  ingredientId: string;
  quantityPerUnit: number;
}

/**
 * Alta/edición/baja de productos — ítem 6 de las mejoras pedidas ("agregar
 * productos desde el front"), con imagen (ítem 4, sube el archivo vía
 * {@link MediaService} y pega la URL devuelta en el form) e ingredientes
 * (ítem 10). Incluye buscador por nombre/SKU y filtro por categoría (mismo
 * espíritu que el buscador de {@code WaiterOrderComponent} — ítem 3 — pero
 * acá filtrando también productos inactivos, útil para reactivarlos).
 */
@Component({
    selector: 'app-product-list',
    imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterLink, IconComponent, ModalComponent, EmptyStateComponent],
    templateUrl: './product-list.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './product-list.component.css'
})
export class ProductListComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly productService = inject(ProductService);
  private readonly categoryService = inject(CategoryService);
  private readonly mediaService = inject(MediaService);
  private readonly authService = inject(AuthService);
  private readonly stockService = inject(StockService);
  private readonly optionGroupService = inject(OptionGroupService);
  private readonly toast = inject(ToastService);

  readonly products = signal<ProductResponse[]>([]);
  readonly categories = signal<CategoryResponse[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly editingId = signal<string | null>(null);
  readonly saving = signal(false);
  readonly creating = signal(false);
  readonly showCreateForm = signal(false);
  readonly uploadingImage = signal(false);
  readonly searchTerm = signal('');
  readonly selectedCategoryId = signal<string | null>(null);

  /**
   * Bloque D (ítems 12/13) — configuración de stock por producto. El
   * catálogo de insumos (para el selector de la receta en modo RECIPE) se
   * carga acá una sola vez junto con productos/categorías; el resto del
   * flujo de stock (niveles, ajustes manuales, historial) vive en
   * {@code IngredientListComponent} (pantalla "Insumos y Stock").
   */
  readonly stockModeLabels = STOCK_MODE_LABELS;
  readonly ingredients = signal<IngredientResponse[]>([]);
  readonly configuringStockId = signal<string | null>(null);
  readonly stockConfigMode = signal<StockMode>('NONE');
  readonly stockConfigMinStock = signal<number | null>(null);
  readonly stockConfigRecipe = signal<RecipeRow[]>([]);
  readonly loadingRecipe = signal(false);
  readonly savingStockConfig = signal(false);
  readonly stockConfigError = signal<string | null>(null);

  readonly activeIngredients = computed(() => this.ingredients().filter((i) => i.isActive));

  /**
   * V11 — grupos de opciones (tamaño, leche, extras). Se crean en la
   * pantalla "Opciones"; acá solo se eligen cuáles usa cada producto.
   */
  readonly optionGroups = signal<OptionGroupResponse[]>([]);
  readonly configuringOptionsId = signal<string | null>(null);
  readonly optionsSelection = signal<ReadonlySet<string>>(new Set());
  readonly savingOptions = signal(false);
  readonly ruleLabel = groupRuleLabel;

  private readonly groupNamesByProduct = computed(() => {
    const map: Record<string, string[]> = {};
    for (const group of this.optionGroups()) {
      for (const productId of group.productIds) (map[productId] ??= []).push(group.name);
    }
    return map;
  });

  /** Alta/edición/baja exige OWNER o ADMIN — ver ProductController del backend. */
  readonly canManage = computed(() => {
    const role = this.authService.user()?.role;
    return role === 'OWNER' || role === 'ADMIN';
  });

  readonly categoriesById = computed<Record<string, CategoryResponse>>(() => {
    const map: Record<string, CategoryResponse> = {};
    for (const category of this.categories()) map[category.id] = category;
    return map;
  });

  readonly activeCategories = computed(() =>
    this.categories()
      .filter((category) => category.isActive)
      .sort((a, b) => a.displayOrder - b.displayOrder),
  );

  readonly filteredProducts = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    const categoryId = this.selectedCategoryId();
    return [...this.products()]
      .filter((product) => !categoryId || product.categoryId === categoryId)
      .filter(
        (product) =>
          !term || product.name.toLowerCase().includes(term) || (product.sku ?? '').toLowerCase().includes(term),
      )
      .sort((a, b) => a.name.localeCompare(b.name));
  });

  readonly createForm = this.fb.nonNullable.group({
    categoryId: ['', [Validators.required]],
    name: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(150)]],
    description: [''],
    ingredients: [''],
    sku: [''],
    price: [0, [Validators.required, Validators.min(0)]],
    imageUrl: [''],
  });

  readonly editForm = this.fb.nonNullable.group({
    categoryId: ['', [Validators.required]],
    name: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(150)]],
    description: [''],
    ingredients: [''],
    sku: [''],
    price: [0, [Validators.required, Validators.min(0)]],
    imageUrl: [''],
  });

  async ngOnInit(): Promise<void> {
    await this.reload();
  }

  async reload(): Promise<void> {
    this.loading.set(true);
    this.errorMessage.set(null);
    try {
      const [products, categories, ingredients, optionGroups] = await Promise.all([
        this.productService.findAll(),
        this.categoryService.findAll(),
        this.stockService.findAllIngredients(),
        this.optionGroupService.findAll(),
      ]);
      this.optionGroups.set(optionGroups);
      this.products.set(products);
      this.categories.set(categories);
      this.ingredients.set(ingredients);
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudieron cargar los productos.'));
    } finally {
      this.loading.set(false);
    }
  }

  categoryName(categoryId: string): string {
    return this.categoriesById()[categoryId]?.name ?? 'Sin categoría';
  }

  toggleCreateForm(): void {
    this.showCreateForm.set(!this.showCreateForm());
    // Arranca con la categoría que se está mirando (si hay una elegida): un paso menos.
    this.createForm.reset({
      categoryId: this.selectedCategoryId() ?? (this.activeCategories().length === 1 ? this.activeCategories()[0].id : ''),
      name: '',
      description: '',
      ingredients: '',
      sku: '',
      price: 0,
      imageUrl: '',
    });
  }

  editingProduct(): ProductResponse | null {
    return this.products().find((product) => product.id === this.editingId()) ?? null;
  }

  optionsProduct(): ProductResponse | null {
    return this.products().find((product) => product.id === this.configuringOptionsId()) ?? null;
  }

  /** "Tamaño, Leche" — los grupos que usa el producto ('' si ninguno). */
  optionGroupNames(productId: string): string {
    return (this.groupNamesByProduct()[productId] ?? []).join(', ');
  }

  openOptions(product: ProductResponse): void {
    this.editingId.set(null);
    this.configuringStockId.set(null);
    this.optionsSelection.set(
      new Set(this.optionGroups().filter((group) => group.productIds.includes(product.id)).map((group) => group.id)),
    );
    this.configuringOptionsId.set(product.id);
  }

  toggleOptionGroup(groupId: string): void {
    const next = new Set(this.optionsSelection());
    if (next.has(groupId)) next.delete(groupId);
    else next.add(groupId);
    this.optionsSelection.set(next);
  }

  async saveOptions(): Promise<void> {
    const productId = this.configuringOptionsId();
    if (!productId || this.savingOptions()) return;
    this.savingOptions.set(true);
    try {
      const chosen = this.optionsSelection();
      // Respeta el orden de los grupos (el mismo en que se preguntan al pedir).
      const groupIds = this.optionGroups().filter((group) => chosen.has(group.id)).map((group) => group.id);
      await this.optionGroupService.setProductGroups(productId, groupIds);
      this.optionGroups.set(
        this.optionGroups().map((group) => {
          const others = group.productIds.filter((id) => id !== productId);
          return { ...group, productIds: chosen.has(group.id) ? [...others, productId] : others };
        }),
      );
      this.configuringOptionsId.set(null);
      this.toast.success('Opciones del producto guardadas.');
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudieron guardar las opciones.'));
    } finally {
      this.savingOptions.set(false);
    }
  }

  configuringProduct(): ProductResponse | null {
    return this.products().find((product) => product.id === this.configuringStockId()) ?? null;
  }

  productInitials(name: string): string {
    return name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((word) => word[0])
      .join('')
      .toUpperCase();
  }

  countIn(categoryId: string | null): number {
    return this.products().filter((product) => !categoryId || product.categoryId === categoryId).length;
  }

  /**
   * Sube el archivo elegido y pega la URL pública devuelta en el form
   * correspondiente — subir la imagen y guardar el producto quedan como
   * dos pasos separados a propósito (ver MediaController del backend),
   * así el mismo picker sirve para alta y edición.
   */
  async onImageSelected(event: Event, form: 'create' | 'edit'): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    this.uploadingImage.set(true);
    this.errorMessage.set(null);
    try {
      const { url } = await this.mediaService.uploadProductImage(file);
      const target = form === 'create' ? this.createForm : this.editForm;
      target.controls.imageUrl.setValue(url);
      target.markAsDirty();
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo subir la imagen.'));
    } finally {
      this.uploadingImage.set(false);
      input.value = '';
    }
  }

  async createProduct(): Promise<void> {
    if (this.createForm.invalid || this.creating()) return;
    this.creating.set(true);
    this.errorMessage.set(null);
    try {
      const raw = this.createForm.getRawValue();
      const created = await this.productService.create({
        categoryId: raw.categoryId,
        name: raw.name,
        description: raw.description.trim() || null,
        ingredients: raw.ingredients.trim() || null,
        sku: raw.sku.trim() || null,
        price: raw.price,
        imageUrl: raw.imageUrl.trim() || null,
      });
      this.products.set([...this.products(), created]);
      this.showCreateForm.set(false);
      this.createForm.reset({ categoryId: '', name: '', description: '', ingredients: '', sku: '', price: 0, imageUrl: '' });
      this.toast.success(`"${created.name}" ya está en la carta.`);
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo crear el producto.'));
    } finally {
      this.creating.set(false);
    }
  }

  startEdit(product: ProductResponse): void {
    this.configuringStockId.set(null); // Un solo panel abierto por producto a la vez — evita clutter.
    this.editingId.set(product.id);
    this.editForm.setValue({
      categoryId: product.categoryId,
      name: product.name,
      description: product.description ?? '',
      ingredients: product.ingredients ?? '',
      sku: product.sku ?? '',
      price: product.price,
      imageUrl: product.imageUrl ?? '',
    });
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
      const updated = await this.productService.update(id, {
        categoryId: raw.categoryId,
        name: raw.name,
        description: raw.description.trim() || null,
        ingredients: raw.ingredients.trim() || null,
        sku: raw.sku.trim() || null,
        price: raw.price,
        imageUrl: raw.imageUrl.trim() || null,
      });
      this.products.set(this.products().map((product) => (product.id === id ? updated : product)));
      this.editingId.set(null);
      this.toast.success('Producto guardado.');
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo guardar el producto.'));
    } finally {
      this.saving.set(false);
    }
  }

  /** Baja lógica vía DELETE, reactivación vía PATCH {isActive: true} — ver ProductController del backend. */
  async toggleActive(product: ProductResponse): Promise<void> {
    if (this.saving()) return;
    this.saving.set(true);
    this.errorMessage.set(null);
    try {
      let updated: ProductResponse;
      if (product.isActive) {
        await this.productService.deactivate(product.id);
        updated = { ...product, isActive: false };
      } else {
        updated = await this.productService.update(product.id, { isActive: true });
      }
      this.products.set(this.products().map((p) => (p.id === product.id ? updated : p)));
      this.editingId.set(null);
      this.toast.info(updated.isActive ? `"${product.name}" vuelve a la carta.` : `"${product.name}" dado de baja.`);
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo cambiar el estado del producto.'));
    } finally {
      this.saving.set(false);
    }
  }

  // ---------------------------------------------------------------------
  // Bloque D (ítems 12/13) — configuración de stock del producto
  // ---------------------------------------------------------------------

  /** Abre/cierra el panel de configuración de stock — carga la receta actual si el producto ya está en modo RECIPE. */
  async toggleStockConfig(product: ProductResponse): Promise<void> {
    if (this.configuringStockId() === product.id) {
      this.configuringStockId.set(null);
      return;
    }
    this.editingId.set(null); // Un solo panel abierto por producto a la vez.
    this.configuringStockId.set(product.id);
    this.stockConfigMode.set(product.stockMode);
    this.stockConfigMinStock.set(product.minStock);
    this.stockConfigRecipe.set([]);
    this.stockConfigError.set(null);
    if (product.stockMode === 'RECIPE') {
      await this.loadRecipe(product.id);
    }
  }

  /** Cambiar a RECIPE desde el selector (sin haber arrancado ya en ese modo) también trae la receta actual, si hay una guardada. */
  async onStockModeChange(productId: string, mode: StockMode): Promise<void> {
    this.stockConfigMode.set(mode);
    if (mode === 'RECIPE' && this.stockConfigRecipe().length === 0) {
      await this.loadRecipe(productId);
    }
  }

  private async loadRecipe(productId: string): Promise<void> {
    this.loadingRecipe.set(true);
    try {
      const items = await this.stockService.productRecipe(productId);
      this.stockConfigRecipe.set(items.map((item) => ({ ingredientId: item.ingredientId, quantityPerUnit: item.quantityPerUnit })));
    } catch (error) {
      this.stockConfigError.set(extractErrorMessage(error, 'No se pudo cargar la receta.'));
    } finally {
      this.loadingRecipe.set(false);
    }
  }

  addRecipeRow(): void {
    const firstIngredient = this.activeIngredients()[0]?.id ?? '';
    this.stockConfigRecipe.set([...this.stockConfigRecipe(), { ingredientId: firstIngredient, quantityPerUnit: 0 }]);
  }

  removeRecipeRow(index: number): void {
    this.stockConfigRecipe.set(this.stockConfigRecipe().filter((_, i) => i !== index));
  }

  updateRecipeIngredient(index: number, ingredientId: string): void {
    this.stockConfigRecipe.set(this.stockConfigRecipe().map((row, i) => (i === index ? { ...row, ingredientId } : row)));
  }

  updateRecipeQuantity(index: number, quantityPerUnit: number): void {
    this.stockConfigRecipe.set(this.stockConfigRecipe().map((row, i) => (i === index ? { ...row, quantityPerUnit } : row)));
  }

  cancelStockConfig(): void {
    this.configuringStockId.set(null);
  }

  /**
   * Reemplaza la receta COMPLETA cuando el modo es RECIPE (ver el Javadoc
   * de {@code ProductRecipeItem}/{@code StockService#replaceRecipe} del
   * backend) — nunca un PATCH incremental. En DIRECT solo viaja {@code
   * minStock}; en NONE ninguno de los dos.
   */
  async saveStockConfig(productId: string): Promise<void> {
    if (this.savingStockConfig()) return;
    const mode = this.stockConfigMode();
    if (mode === 'RECIPE' && this.stockConfigRecipe().length === 0) {
      this.stockConfigError.set('Agregá al menos un insumo a la receta (o elegí otro modo de stock).');
      return;
    }
    if (mode === 'RECIPE' && this.stockConfigRecipe().some((row) => !row.ingredientId || row.quantityPerUnit <= 0)) {
      this.stockConfigError.set('Completá insumo y cantidad (mayor a 0) en cada línea de la receta.');
      return;
    }

    this.savingStockConfig.set(true);
    this.stockConfigError.set(null);
    try {
      const updated = await this.stockService.updateProductStockConfig(productId, {
        stockMode: mode,
        minStock: mode === 'DIRECT' ? this.stockConfigMinStock() : null,
        recipeItems: mode === 'RECIPE' ? this.stockConfigRecipe() : undefined,
      });
      this.products.set(this.products().map((p) => (p.id === productId ? updated : p)));
      this.configuringStockId.set(null);
      this.toast.success('Stock del producto configurado.');
    } catch (error) {
      this.stockConfigError.set(extractErrorMessage(error, 'No se pudo guardar la configuración de stock.'));
    } finally {
      this.savingStockConfig.set(false);
    }
  }
}
