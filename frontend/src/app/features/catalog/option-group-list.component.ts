import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CategoryResponse } from '../../core/models/category.model';
import { OptionGroupResponse, SaveOptionGroupInput, groupRuleLabel } from '../../core/models/option.model';
import { ProductResponse } from '../../core/models/product.model';
import { IngredientResponse } from '../../core/models/stock.model';
import { CategoryService, ProductService } from '../../core/services/catalog.service';
import { extractErrorMessage } from '../../core/services/error.util';
import { OptionGroupService } from '../../core/services/option-group.service';
import { StockService } from '../../core/services/stock.service';
import { ToastService } from '../../core/services/toast.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { ModalComponent } from '../../shared/components/modal/modal.component';

/** Una opción en edición (antes de guardar). */
interface OptionRow {
  id: string | null;
  name: string;
  priceDelta: number | null;
  isDefault: boolean;
  isAvailable: boolean;
  ingredientId: string;
  ingredientQuantity: number | null;
  showIngredient: boolean;
}

type Choice = 'single' | 'multiple';

interface Example {
  name: string;
  required: boolean;
  choice: Choice;
  options: { name: string; price: number; isDefault?: boolean }[];
}

/** Para arrancar con un toque: los tres grupos que casi todo café usa. */
const EXAMPLES: readonly Example[] = [
  {
    name: 'Tamaño',
    required: true,
    choice: 'single',
    options: [
      { name: 'Chico', price: 0, isDefault: true },
      { name: 'Mediano', price: 400 },
      { name: 'Grande', price: 800 },
    ],
  },
  {
    name: 'Leche',
    required: true,
    choice: 'single',
    options: [
      { name: 'Entera', price: 0, isDefault: true },
      { name: 'Descremada', price: 0 },
      { name: 'Deslactosada', price: 0 },
      { name: 'Almendra', price: 300 },
      { name: 'Avena', price: 300 },
    ],
  },
  {
    name: 'Extras',
    required: false,
    choice: 'multiple',
    options: [
      { name: 'Extra shot', price: 500 },
      { name: 'Crema', price: 400 },
      { name: 'Caramelo', price: 300 },
      { name: 'Canela', price: 0 },
    ],
  },
];

function emptyRow(): OptionRow {
  return {
    id: null,
    name: '',
    priceDelta: 0,
    isDefault: false,
    isAvailable: true,
    ingredientId: '',
    ingredientQuantity: null,
    showIngredient: false,
  };
}

/**
 * Pantalla "Opciones" (V11): grupos reutilizables de variantes y agregados
 * — "Tamaño", "Leche", "Extras" — y a qué productos se aplican. Solo
 * OWNER/ADMIN (mismo criterio que el alta de productos).
 */
@Component({
    selector: 'app-option-group-list',
    imports: [CommonModule, FormsModule, RouterLink, IconComponent, ModalComponent, EmptyStateComponent],
    templateUrl: './option-group-list.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './option-group-list.component.css'
})
export class OptionGroupListComponent implements OnInit {
  private readonly optionGroupService = inject(OptionGroupService);
  private readonly productService = inject(ProductService);
  private readonly categoryService = inject(CategoryService);
  private readonly stockService = inject(StockService);
  private readonly toast = inject(ToastService);

  readonly examples = EXAMPLES;
  readonly ruleLabel = groupRuleLabel;

  readonly groups = signal<OptionGroupResponse[]>([]);
  readonly products = signal<ProductResponse[]>([]);
  readonly categories = signal<CategoryResponse[]>([]);
  readonly ingredients = signal<IngredientResponse[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly confirmingDeleteId = signal<string | null>(null);
  readonly deleting = signal(false);
  readonly reordering = signal(false);

  // ---- Editor ----
  readonly editorOpen = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly name = signal('');
  readonly required = signal(false);
  readonly choice = signal<Choice>('single');
  /** Solo con "varias": null = sin límite. */
  readonly maxMultiple = signal<number | null>(null);
  readonly rows = signal<OptionRow[]>([]);
  readonly productIds = signal<ReadonlySet<string>>(new Set());
  readonly productSearch = signal('');
  readonly saving = signal(false);
  readonly editorError = signal<string | null>(null);

  readonly activeIngredients = computed(() => this.ingredients().filter((ingredient) => ingredient.isActive));

  private readonly productsById = computed(() => {
    const map: Record<string, ProductResponse> = {};
    for (const product of this.products()) map[product.id] = product;
    return map;
  });

  /** Productos activos agrupados por categoría (en el orden de la carta), filtrados por el buscador. */
  readonly productsByCategory = computed(() => {
    const term = this.productSearch().trim().toLowerCase();
    const active = this.products()
      .filter((product) => product.isActive)
      .filter((product) => !term || product.name.toLowerCase().includes(term))
      .sort((a, b) => a.name.localeCompare(b.name));
    return [...this.categories()]
      .sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name))
      .map((category) => ({ category, products: active.filter((product) => product.categoryId === category.id) }))
      .filter((section) => section.products.length > 0);
  });

  readonly selectedCount = computed(() => this.productIds().size);

  /** Ejemplos que todavía no se crearon (para sumarlos con un toque). */
  readonly remainingExamples = computed(() => {
    const taken = new Set(this.groups().map((group) => group.name.trim().toLowerCase()));
    return EXAMPLES.filter((example) => !taken.has(example.name.toLowerCase()));
  });

  async ngOnInit(): Promise<void> {
    try {
      const [groups, products, categories, ingredients] = await Promise.all([
        this.optionGroupService.findAll(),
        this.productService.findAll(),
        this.categoryService.findAll(),
        this.stockService.findAllIngredients(),
      ]);
      this.groups.set(groups);
      this.products.set(products);
      this.categories.set(categories);
      this.ingredients.set(ingredients);
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudieron cargar las opciones.'));
    } finally {
      this.loading.set(false);
    }
  }

  /** "Cortado, Latte y 3 más". */
  productsSummary(group: OptionGroupResponse): string {
    const names = group.productIds
      .map((id) => this.productsById()[id])
      .filter((product): product is ProductResponse => !!product && product.isActive)
      .map((product) => product.name)
      .sort((a, b) => a.localeCompare(b));
    if (names.length === 0) return 'Todavía no se usa en ningún producto';
    if (names.length <= 3) return names.join(', ');
    return `${names.slice(0, 3).join(', ')} y ${names.length - 3} más`;
  }

  // ---- Abrir el editor ----

  openNew(example?: Example): void {
    this.editingId.set(null);
    this.name.set(example?.name ?? '');
    this.required.set(example?.required ?? false);
    this.choice.set(example?.choice ?? 'single');
    this.maxMultiple.set(null);
    this.rows.set(
      example
        ? example.options.map((option) => ({ ...emptyRow(), name: option.name, priceDelta: option.price, isDefault: !!option.isDefault }))
        : [emptyRow(), emptyRow()],
    );
    this.productIds.set(new Set());
    this.openEditor();
  }

  openEdit(group: OptionGroupResponse): void {
    this.editingId.set(group.id);
    this.name.set(group.name);
    this.required.set(group.minSelect > 0);
    this.choice.set(group.maxSelect === 1 ? 'single' : 'multiple');
    this.maxMultiple.set(group.maxSelect === 1 ? null : group.maxSelect);
    this.rows.set(
      group.options.map((option) => ({
        id: option.id,
        name: option.name,
        priceDelta: option.priceDelta,
        isDefault: option.isDefault,
        isAvailable: option.isAvailable,
        ingredientId: option.ingredientId ?? '',
        ingredientQuantity: option.ingredientQuantity,
        showIngredient: !!option.ingredientId,
      })),
    );
    this.productIds.set(new Set(group.productIds));
    this.openEditor();
  }

  private openEditor(): void {
    this.productSearch.set('');
    this.editorError.set(null);
    this.confirmingDeleteId.set(null);
    this.editorOpen.set(true);
  }

  closeEditor(): void {
    if (!this.saving()) this.editorOpen.set(false);
  }

  // ---- Filas de opciones ----

  addRow(): void {
    this.rows.set([...this.rows(), emptyRow()]);
  }

  removeRow(index: number): void {
    this.rows.set(this.rows().filter((_, i) => i !== index));
  }

  moveRow(index: number, delta: -1 | 1): void {
    const target = index + delta;
    const rows = [...this.rows()];
    if (target < 0 || target >= rows.length) return;
    [rows[index], rows[target]] = [rows[target], rows[index]];
    this.rows.set(rows);
  }

  updateRow(index: number, patch: Partial<OptionRow>): void {
    this.rows.set(this.rows().map((row, i) => (i === index ? { ...row, ...patch } : row)));
  }

  /** "De entrada": en un grupo de una sola opción, marcar una desmarca las demás. */
  toggleDefault(index: number): void {
    const turningOn = !this.rows()[index].isDefault;
    this.rows.set(
      this.rows().map((row, i) => {
        if (i === index) return { ...row, isDefault: turningOn };
        return turningOn && this.choice() === 'single' ? { ...row, isDefault: false } : row;
      }),
    );
  }

  setChoice(choice: Choice): void {
    this.choice.set(choice);
    if (choice === 'single') {
      // Deja una sola "de entrada".
      let seen = false;
      this.rows.set(
        this.rows().map((row) => {
          if (!row.isDefault) return row;
          if (seen) return { ...row, isDefault: false };
          seen = true;
          return row;
        }),
      );
    }
  }

  toggleIngredient(index: number): void {
    const row = this.rows()[index];
    this.updateRow(
      index,
      row.showIngredient
        ? { showIngredient: false, ingredientId: '', ingredientQuantity: null }
        : { showIngredient: true, ingredientId: this.activeIngredients()[0]?.id ?? '' },
    );
  }

  ingredientUnit(ingredientId: string): string {
    return this.ingredients().find((ingredient) => ingredient.id === ingredientId)?.unit ?? '';
  }

  // ---- Productos ----

  isProductSelected(productId: string): boolean {
    return this.productIds().has(productId);
  }

  toggleProduct(productId: string): void {
    const next = new Set(this.productIds());
    if (next.has(productId)) next.delete(productId);
    else next.add(productId);
    this.productIds.set(next);
  }

  allSelected(products: ProductResponse[]): boolean {
    return products.every((product) => this.productIds().has(product.id));
  }

  /** Marca o desmarca toda la categoría de una vez ("todos los cafés"). */
  toggleCategory(products: ProductResponse[]): void {
    const next = new Set(this.productIds());
    const selectAll = !this.allSelected(products);
    for (const product of products) {
      if (selectAll) next.add(product.id);
      else next.delete(product.id);
    }
    this.productIds.set(next);
  }

  // ---- Guardar / borrar ----

  async save(): Promise<void> {
    if (this.saving()) return;
    const problem = this.validate();
    if (problem) {
      this.editorError.set(problem);
      return;
    }
    const multiple = this.choice() === 'multiple';
    const input: SaveOptionGroupInput = {
      name: this.name().trim(),
      minSelect: this.required() ? 1 : 0,
      maxSelect: multiple ? this.maxMultiple() || null : 1,
      options: this.rows().map((row) => ({
        id: row.id,
        name: row.name.trim(),
        priceDelta: row.priceDelta ?? 0,
        isDefault: row.isDefault,
        isAvailable: row.isAvailable,
        ingredientId: row.showIngredient && row.ingredientId ? row.ingredientId : null,
        ingredientQuantity: row.showIngredient && row.ingredientId ? row.ingredientQuantity : null,
      })),
      productIds: [...this.productIds()],
    };

    this.saving.set(true);
    this.editorError.set(null);
    try {
      const id = this.editingId();
      const saved = id ? await this.optionGroupService.update(id, input) : await this.optionGroupService.create(input);
      this.groups.set(id ? this.groups().map((group) => (group.id === id ? saved : group)) : [...this.groups(), saved]);
      this.editorOpen.set(false);
      this.toast.success(id ? `"${saved.name}" guardado.` : `"${saved.name}" creado.`);
    } catch (error) {
      this.editorError.set(extractErrorMessage(error, 'No se pudo guardar el grupo.'));
    } finally {
      this.saving.set(false);
    }
  }

  /** Lo mismo que valida el backend, con mensajes antes de mandar. */
  private validate(): string | null {
    if (!this.name().trim()) return 'Ponele un nombre al grupo (por ejemplo "Leche").';
    const rows = this.rows();
    if (rows.length === 0) return 'Agregá al menos una opción.';
    const seen = new Set<string>();
    for (const row of rows) {
      const name = row.name.trim();
      if (!name) return 'Hay una opción sin nombre: completala o quitala.';
      if (seen.has(name.toLowerCase())) return `La opción "${name}" está repetida.`;
      seen.add(name.toLowerCase());
      if (row.priceDelta !== null && row.priceDelta < 0) return `En "${name}": el precio extra no puede ser negativo.`;
      if (row.showIngredient && row.ingredientId && !(row.ingredientQuantity && row.ingredientQuantity > 0)) {
        return `En "${name}": poné cuánto insumo gasta (o quitá el insumo).`;
      }
    }
    const max = this.choice() === 'single' ? 1 : this.maxMultiple() || null;
    if (max !== null && max < 1) return 'El máximo tiene que ser 1 o más.';
    const defaults = rows.filter((row) => row.isDefault).length;
    if (max !== null && defaults > max) return `Marcaste ${defaults} de entrada, pero se pueden elegir hasta ${max}.`;
    return null;
  }

  /** Sube/baja un grupo: es el orden en que se pregunta al tomar el pedido. */
  async moveGroup(index: number, delta: -1 | 1): Promise<void> {
    const target = index + delta;
    const groups = [...this.groups()];
    if (target < 0 || target >= groups.length || this.reordering()) return;
    const previous = this.groups();
    [groups[index], groups[target]] = [groups[target], groups[index]];
    this.groups.set(groups); // se ve al instante; si falla, vuelve atrás
    this.reordering.set(true);
    try {
      this.groups.set(await this.optionGroupService.reorder(groups.map((group) => group.id)));
    } catch (error) {
      this.groups.set(previous);
      this.toast.error(extractErrorMessage(error, 'No se pudo cambiar el orden.'));
    } finally {
      this.reordering.set(false);
    }
  }

  askDelete(group: OptionGroupResponse): void {
    this.confirmingDeleteId.set(group.id);
  }

  async confirmDelete(group: OptionGroupResponse): Promise<void> {
    if (this.deleting()) return;
    this.deleting.set(true);
    try {
      await this.optionGroupService.delete(group.id);
      this.groups.set(this.groups().filter((g) => g.id !== group.id));
      this.toast.info(`"${group.name}" borrado. Los pedidos ya hechos no cambian.`);
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo borrar el grupo.'));
    } finally {
      this.deleting.set(false);
      this.confirmingDeleteId.set(null);
    }
  }
}
