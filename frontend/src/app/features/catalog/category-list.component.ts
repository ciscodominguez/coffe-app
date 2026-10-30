import { Component, OnInit, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { CategoryResponse } from '../../core/models/category.model';
import { CategoryService } from '../../core/services/catalog.service';
import { extractErrorMessage } from '../../core/services/error.util';
import { ToastService } from '../../core/services/toast.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { ModalComponent } from '../../shared/components/modal/modal.component';

/**
 * Alta/edición/baja de categorías — ítem 6 de las mejoras pedidas
 * ("agregar productos desde el front"): esta pantalla cubre el lado
 * Categorías, {@link ProductListComponent} el lado Productos. Mismo
 * patrón de "form + lista con edición inline" que
 * {@code LocationListComponent}.
 */
@Component({
    selector: 'app-category-list',
    imports: [ReactiveFormsModule, IconComponent, ModalComponent, EmptyStateComponent],
    templateUrl: './category-list.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './category-list.component.css'
})
export class CategoryListComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly categoryService = inject(CategoryService);
  private readonly authService = inject(AuthService);
  private readonly toast = inject(ToastService);
  /** Ideas para arrancar rápido cuando todavía no hay ninguna categoría. */
  readonly suggestions: readonly string[] = ['Cafetería', 'Pastelería', 'Salados', 'Bebidas frías', 'Promos'];

  readonly categories = signal<CategoryResponse[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly editingId = signal<string | null>(null);
  readonly saving = signal(false);
  readonly creating = signal(false);
  readonly showCreateForm = signal(false);

  /** Alta/edición/baja exige OWNER o ADMIN — ver CategoryController del backend. */
  readonly canManage = computed(() => {
    const role = this.authService.user()?.role;
    return role === 'OWNER' || role === 'ADMIN';
  });

  readonly sortedCategories = computed(() =>
    [...this.categories()].sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name)),
  );

  readonly createForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(150)]],
    description: [''],
    displayOrder: [0, [Validators.required]],
  });

  readonly editForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(150)]],
    description: [''],
    displayOrder: [0, [Validators.required]],
  });

  async ngOnInit(): Promise<void> {
    await this.reload();
  }

  async reload(): Promise<void> {
    this.loading.set(true);
    this.errorMessage.set(null);
    try {
      this.categories.set(await this.categoryService.findAll());
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudieron cargar las categorías.'));
    } finally {
      this.loading.set(false);
    }
  }

  toggleCreateForm(): void {
    this.showCreateForm.set(!this.showCreateForm());
    // El orden sugerido es "al final de la lista", así no hay que pensarlo.
    const nextOrder = Math.max(0, ...this.categories().map((category) => category.displayOrder + 1));
    this.createForm.reset({ name: '', description: '', displayOrder: nextOrder });
  }

  async createCategory(): Promise<void> {
    if (this.createForm.invalid || this.creating()) return;
    this.creating.set(true);
    this.errorMessage.set(null);
    try {
      const raw = this.createForm.getRawValue();
      const created = await this.categoryService.create({
        name: raw.name,
        description: raw.description.trim() || null,
        displayOrder: raw.displayOrder,
      });
      this.categories.set([...this.categories(), created]);
      this.showCreateForm.set(false);
      this.createForm.reset({ name: '', description: '', displayOrder: 0 });
      this.toast.success(`Categoría "${created.name}" creada.`);
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo crear la categoría.'));
    } finally {
      this.creating.set(false);
    }
  }

  startEdit(category: CategoryResponse): void {
    this.editingId.set(category.id);
    this.editForm.setValue({
      name: category.name,
      description: category.description ?? '',
      displayOrder: category.displayOrder,
    });
  }

  /** Crea una categoría sugerida con un toque (pantalla vacía). */
  async quickCreate(name: string): Promise<void> {
    this.createForm.reset({
      name,
      description: '',
      displayOrder: Math.max(0, ...this.categories().map((category) => category.displayOrder + 1)),
    });
    await this.createCategory();
  }

  /** Subir/bajar en la carta (cambia el orden en que aparece en Pedidos). */
  async move(category: CategoryResponse, direction: -1 | 1): Promise<void> {
    const list = this.sortedCategories();
    const index = list.findIndex((c) => c.id === category.id);
    const other = list[index + direction];
    if (!other || this.saving()) return;
    this.saving.set(true);
    try {
      const a = category.displayOrder === other.displayOrder ? index : category.displayOrder;
      const b = category.displayOrder === other.displayOrder ? index + direction : other.displayOrder;
      const [first, second] = await Promise.all([
        this.categoryService.update(category.id, { displayOrder: b }),
        this.categoryService.update(other.id, { displayOrder: a }),
      ]);
      this.categories.set(
        this.categories().map((c) => (c.id === first.id ? first : c.id === second.id ? second : c)),
      );
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo cambiar el orden.'));
    } finally {
      this.saving.set(false);
    }
  }

  editingCategory(): CategoryResponse | null {
    return this.categories().find((category) => category.id === this.editingId()) ?? null;
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
      const updated = await this.categoryService.update(id, {
        name: raw.name,
        description: raw.description.trim() || null,
        displayOrder: raw.displayOrder,
      });
      this.categories.set(this.categories().map((category) => (category.id === id ? updated : category)));
      this.editingId.set(null);
      this.toast.success('Categoría guardada.');
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo guardar la categoría.'));
    } finally {
      this.saving.set(false);
    }
  }

  /** Baja lógica vía DELETE, reactivación vía PATCH {isActive: true} — ver CategoryController del backend. */
  async toggleActive(category: CategoryResponse): Promise<void> {
    if (this.saving()) return;
    this.saving.set(true);
    this.errorMessage.set(null);
    try {
      let updated: CategoryResponse;
      if (category.isActive) {
        await this.categoryService.deactivate(category.id);
        updated = { ...category, isActive: false };
      } else {
        updated = await this.categoryService.update(category.id, { isActive: true });
      }
      this.categories.set(this.categories().map((c) => (c.id === category.id ? updated : c)));
      this.editingId.set(null);
      this.toast.info(updated.isActive ? `"${category.name}" vuelve a la carta.` : `"${category.name}" dada de baja.`);
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo cambiar el estado de la categoría.'));
    } finally {
      this.saving.set(false);
    }
  }
}
