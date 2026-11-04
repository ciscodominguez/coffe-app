import { DecimalPipe } from '@angular/common';
import { Component, computed, effect, input, output, signal, untracked, ChangeDetectionStrategy } from '@angular/core';
import {
  OptionGroupResponse,
  OptionItemResponse,
  availableOptions,
  effectiveMin,
  groupRuleLabel,
} from '../../../core/models/option.model';
import { ProductResponse } from '../../../core/models/product.model';
import { IconComponent } from '../icon/icon.component';
import { ModalComponent } from '../modal/modal.component';

/** Lo que se eligió, listo para sumar al carrito. */
export interface PickedOptions {
  optionIds: string[];
  /** Nombres en el orden de los grupos, para mostrar en el carrito. */
  labels: string[];
  /** Precio del producto + los extras. */
  unitPrice: number;
}

/**
 * Ventana para elegir tamaño, leche, extras… al sumar un producto al
 * pedido (V11). Viene con lo "de entrada" ya marcado, así en el caso común
 * es un solo toque en "Agregar".
 *
 * - Grupo de una sola opción: se comporta como botones de radio (si es
 *   opcional, tocar la elegida la desmarca).
 * - Grupo de varias: casillas, hasta el máximo.
 * - "No hay": se ve tachada y no se puede tocar.
 */
@Component({
    selector: 'app-option-picker',
    imports: [DecimalPipe, IconComponent, ModalComponent],
    template: `
    <app-modal [open]="!!product()" [title]="product()?.name ?? ''" subtitle="Elegí cómo lo quiere" (closed)="closed.emit()">
      @if (product(); as p) {
        <div class="groups">
          @for (group of groups(); track group.id) {
            <fieldset class="group" [class.missing]="showMissing() && isMissing(group)">
              <legend>
                <span class="group-name">{{ group.name }}</span>
                <span class="rule" [class.required]="minOf(group) > 0">{{ ruleLabel(group) }}</span>
              </legend>
              <div class="options">
                @for (option of group.options; track option.id) {
                  <button
                    type="button"
                    class="opt"
                    [class.selected]="isSelected(option.id)"
                    [class.single]="group.maxSelect === 1"
                    [disabled]="!option.isAvailable || (!isSelected(option.id) && isFull(group) && group.maxSelect !== 1)"
                    [attr.aria-pressed]="isSelected(option.id)"
                    (click)="toggle(group, option)"
                  >
                    <span class="mark" aria-hidden="true">
                      @if (isSelected(option.id)) {
                        <app-icon name="check" [size]="14" />
                      }
                    </span>
                    <span class="opt-text">
                      <span class="opt-name" [class.unavailable]="!option.isAvailable">{{ option.name }}</span>
                      @if (!option.isAvailable) {
                        <span class="opt-extra muted">No hay</span>
                      } @else if (option.priceDelta > 0) {
                        <span class="opt-extra money">+ $ {{ option.priceDelta | number: '1.0-2' }}</span>
                      }
                    </span>
                  </button>
                }
              </div>
              @if (showMissing() && isMissing(group)) {
                <p class="missing-text"><app-icon name="alert" [size]="14" /> Falta elegir {{ group.name.toLowerCase() }}</p>
              }
            </fieldset>
          }
        </div>
      }
      <div modal-footer>
        <button type="button" class="btn btn-ghost" (click)="closed.emit()">Cancelar</button>
        <button type="button" class="btn btn-primary" (click)="confirm()">
          <app-icon name="plus" [size]="18" /> Agregar · <span class="money">$ {{ unitPrice() | number: '1.2-2' }}</span>
        </button>
      </div>
    </app-modal>
  `,
    changeDetection: ChangeDetectionStrategy.Eager,
    styles: [
        `
      .groups {
        display: flex;
        flex-direction: column;
        gap: 1.1rem;
      }
      .group {
        border: none;
        margin: 0;
        padding: 0;
        min-width: 0;
      }
      legend {
        display: flex;
        align-items: baseline;
        gap: 0.6rem;
        flex-wrap: wrap;
        padding: 0;
        margin-bottom: 0.5rem;
      }
      .group-name {
        font-weight: 700;
        font-size: 1rem;
      }
      .rule {
        font-size: 0.78rem;
        color: var(--text-muted);
      }
      .rule.required {
        color: var(--primary);
        font-weight: 650;
      }
      .options {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(min(100%, 150px), 1fr));
        gap: 0.5rem;
      }
      .opt {
        display: flex;
        align-items: center;
        gap: 0.55rem;
        min-height: 52px;
        padding: 0.5rem 0.75rem;
        border-radius: var(--radius);
        border: 1.5px solid var(--border-strong);
        background: var(--surface);
        color: var(--text);
        font: inherit;
        text-align: left;
        cursor: pointer;
        transition:
          border-color var(--transition),
          background var(--transition);
      }
      .opt:hover:not(:disabled) {
        background: var(--surface-2);
      }
      .opt.selected {
        border-color: var(--primary);
        background: var(--primary-soft);
      }
      .opt:disabled {
        cursor: not-allowed;
        opacity: 0.55;
      }
      .mark {
        flex: none;
        width: 20px;
        height: 20px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border: 1.5px solid var(--border-strong);
        border-radius: 5px;
        color: var(--on-primary);
      }
      .opt.single .mark {
        border-radius: 50%;
      }
      .opt.selected .mark {
        background: var(--primary);
        border-color: var(--primary);
      }
      .opt-text {
        flex: 1;
        min-width: 0;
        display: flex;
        align-items: baseline;
        justify-content: space-between;
        flex-wrap: wrap;
        column-gap: 0.5rem;
      }
      .opt-name {
        font-weight: 600;
        overflow-wrap: anywhere;
      }
      .opt-name.unavailable {
        text-decoration: line-through;
      }
      .opt-extra {
        font-size: 0.85rem;
        white-space: nowrap;
      }
      .group.missing .opt:not(:disabled) {
        border-color: var(--danger);
      }
      .missing-text {
        display: flex;
        align-items: center;
        gap: 0.35rem;
        margin: 0.4rem 0 0;
        font-size: 0.82rem;
        color: var(--danger);
        font-weight: 600;
      }
    `,
    ]
})
export class OptionPickerComponent {
  /** El producto a elegir; null = ventana cerrada. */
  readonly product = input<ProductResponse | null>(null);
  /** Los grupos de ese producto (ya ordenados). */
  readonly groups = input<OptionGroupResponse[]>([]);

  readonly confirmed = output<PickedOptions>();
  readonly closed = output<void>();

  private readonly selected = signal<ReadonlySet<string>>(new Set());
  readonly showMissing = signal(false);

  readonly unitPrice = computed(() => {
    const base = this.product()?.price ?? 0;
    const chosen = this.selected();
    let extra = 0;
    for (const group of this.groups()) {
      for (const option of group.options) if (chosen.has(option.id)) extra += option.priceDelta;
    }
    return base + extra;
  });

  constructor() {
    // Cada vez que se abre para un producto: arranca con lo "de entrada".
    effect(() => {
      const product = this.product();
      const groups = this.groups();
      untracked(() => {
        this.showMissing.set(false);
        if (!product) return;
        const initial = new Set<string>();
        for (const group of groups) {
          const defaults = availableOptions(group).filter((option) => option.isDefault);
          const limit = group.maxSelect ?? defaults.length;
          defaults.slice(0, limit).forEach((option) => initial.add(option.id));
        }
        this.selected.set(initial);
      });
    });
  }

  ruleLabel(group: OptionGroupResponse): string {
    return groupRuleLabel({ minSelect: this.minOf(group), maxSelect: group.maxSelect });
  }

  minOf(group: OptionGroupResponse): number {
    return effectiveMin(group);
  }

  isSelected(optionId: string): boolean {
    return this.selected().has(optionId);
  }

  private countIn(group: OptionGroupResponse): number {
    const chosen = this.selected();
    return group.options.filter((option) => chosen.has(option.id)).length;
  }

  isFull(group: OptionGroupResponse): boolean {
    return group.maxSelect !== null && this.countIn(group) >= group.maxSelect;
  }

  isMissing(group: OptionGroupResponse): boolean {
    return this.countIn(group) < this.minOf(group);
  }

  toggle(group: OptionGroupResponse, option: OptionItemResponse): void {
    if (!option.isAvailable) return;
    const next = new Set(this.selected());
    if (group.maxSelect === 1) {
      const wasSelected = next.has(option.id);
      group.options.forEach((other) => next.delete(other.id));
      // Opcional de una sola: tocar la elegida la desmarca.
      if (!wasSelected || this.minOf(group) > 0) next.add(option.id);
    } else if (next.has(option.id)) {
      next.delete(option.id);
    } else if (!this.isFull(group)) {
      next.add(option.id);
    }
    this.selected.set(next);
  }

  confirm(): void {
    if (this.groups().some((group) => this.isMissing(group))) {
      this.showMissing.set(true);
      return;
    }
    const chosen = this.selected();
    const optionIds: string[] = [];
    const labels: string[] = [];
    for (const group of this.groups()) {
      for (const option of group.options) {
        if (chosen.has(option.id)) {
          optionIds.push(option.id);
          labels.push(option.name);
        }
      }
    }
    this.confirmed.emit({ optionIds, labels, unitPrice: this.unitPrice() });
  }
}
