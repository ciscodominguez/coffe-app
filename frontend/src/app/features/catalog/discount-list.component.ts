import { Component, OnInit, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DiscountResponse, SaveDiscountInput } from '../../core/models/discount.model';
import { DiscountType, discountValueLabel } from '../../core/models/order.model';
import { DiscountService } from '../../core/services/discount.service';
import { extractErrorMessage } from '../../core/services/error.util';
import { ToastService } from '../../core/services/toast.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { ModalComponent } from '../../shared/components/modal/modal.component';

/** Para arrancar con un toque. */
const EXAMPLES: readonly SaveDiscountInput[] = [
  { name: 'Jubilados', type: 'PERCENTAGE', value: 10, requiresAuthorization: false },
  { name: 'Empleados', type: 'PERCENTAGE', value: 20, requiresAuthorization: true },
  { name: 'Cortesía de la casa', type: 'FIXED_AMOUNT', value: 1000, requiresAuthorization: true },
];

/**
 * Pantalla "Descuentos" (V12): la lista que se elige con un toque al
 * cobrar. Solo OWNER/ADMIN. Los descuentos a mano (sin lista) se ponen
 * directo en la ventana de cobro, con motivo.
 */
@Component({
    selector: 'app-discount-list',
    imports: [FormsModule, IconComponent, ModalComponent, EmptyStateComponent],
    templateUrl: './discount-list.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './discount-list.component.css'
})
export class DiscountListComponent implements OnInit {
  private readonly discountService = inject(DiscountService);
  private readonly toast = inject(ToastService);

  readonly examples = EXAMPLES;
  readonly valueLabel = discountValueLabel;

  readonly discounts = signal<DiscountResponse[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  readonly editorOpen = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly name = signal('');
  readonly type = signal<DiscountType>('PERCENTAGE');
  readonly value = signal<number | null>(null);
  readonly requiresAuthorization = signal(false);
  readonly isActive = signal(true);
  readonly saving = signal(false);
  readonly editorError = signal<string | null>(null);

  readonly sorted = computed(() =>
    [...this.discounts()].sort((a, b) => Number(b.isActive) - Number(a.isActive) || a.name.localeCompare(b.name)),
  );

  readonly remainingExamples = computed(() => {
    const taken = new Set(this.discounts().map((d) => d.name.toLowerCase()));
    return EXAMPLES.filter((example) => !taken.has(example.name.toLowerCase()));
  });

  async ngOnInit(): Promise<void> {
    try {
      this.discounts.set(await this.discountService.findAll());
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudieron cargar los descuentos.'));
    } finally {
      this.loading.set(false);
    }
  }

  openNew(example?: SaveDiscountInput): void {
    this.editingId.set(null);
    this.name.set(example?.name ?? '');
    this.type.set(example?.type ?? 'PERCENTAGE');
    this.value.set(example?.value ?? null);
    this.requiresAuthorization.set(example?.requiresAuthorization ?? false);
    this.isActive.set(true);
    this.editorError.set(null);
    this.editorOpen.set(true);
  }

  openEdit(discount: DiscountResponse): void {
    this.editingId.set(discount.id);
    this.name.set(discount.name);
    this.type.set(discount.type);
    this.value.set(discount.value);
    this.requiresAuthorization.set(discount.requiresAuthorization);
    this.isActive.set(discount.isActive);
    this.editorError.set(null);
    this.editorOpen.set(true);
  }

  async save(): Promise<void> {
    if (this.saving()) return;
    const name = this.name().trim();
    const value = this.value();
    if (!name) return this.editorError.set('Ponele un nombre (por ejemplo "Jubilados").');
    if (!value || value <= 0) return this.editorError.set('Poné cuánto descuenta (mayor a 0).');
    if (this.type() === 'PERCENTAGE' && value > 100) return this.editorError.set('Un porcentaje no puede pasar del 100%.');

    const input: SaveDiscountInput = {
      name,
      type: this.type(),
      value,
      requiresAuthorization: this.requiresAuthorization(),
      isActive: this.isActive(),
    };
    this.saving.set(true);
    this.editorError.set(null);
    try {
      const id = this.editingId();
      const saved = id ? await this.discountService.update(id, input) : await this.discountService.create(input);
      this.discounts.set(id ? this.discounts().map((d) => (d.id === id ? saved : d)) : [...this.discounts(), saved]);
      this.editorOpen.set(false);
      this.toast.success(id ? `"${saved.name}" guardado.` : `"${saved.name}" creado.`);
    } catch (error) {
      this.editorError.set(extractErrorMessage(error, 'No se pudo guardar el descuento.'));
    } finally {
      this.saving.set(false);
    }
  }

  /** Activar/desactivar directo desde la lista. */
  async toggleActive(discount: DiscountResponse): Promise<void> {
    try {
      let updated: DiscountResponse;
      if (discount.isActive) {
        await this.discountService.deactivate(discount.id);
        updated = { ...discount, isActive: false };
      } else {
        updated = await this.discountService.update(discount.id, {
          name: discount.name,
          type: discount.type,
          value: discount.value,
          requiresAuthorization: discount.requiresAuthorization,
          isActive: true,
        });
      }
      this.discounts.set(this.discounts().map((d) => (d.id === discount.id ? updated : d)));
      this.toast.info(updated.isActive ? `"${discount.name}" vuelve a ofrecerse.` : `"${discount.name}" desactivado.`);
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo cambiar el descuento.'));
    }
  }
}
