import { DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject, input, output, signal, untracked, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DiscountResponse } from '../../../core/models/discount.model';
import {
  DiscountType,
  OrderDiscountResponse,
  OrderResponse,
  discountValueLabel,
  itemOptionsLabel,
} from '../../../core/models/order.model';
import { AuthService } from '../../../core/services/auth.service';
import { DiscountService } from '../../../core/services/discount.service';
import { extractErrorMessage } from '../../../core/services/error.util';
import { OrderService } from '../../../core/services/order.service';
import { ToastService } from '../../../core/services/toast.service';
import { IconComponent } from '../icon/icon.component';

const SUPERVISOR_ROLES = ['SUPERVISOR', 'ADMIN', 'OWNER'];

/**
 * Descuentos dentro de la ventana de cobro (V12).
 *
 * - Muestra los aplicados, con "Quitar".
 * - "Aplicar descuento": a todo el pedido o a un producto; de la lista con
 *   un toque o, si sos supervisor/admin/dueño, uno a mano con motivo.
 *
 * Decisión de Fran: el mozo usa la lista (salvo los que "piden
 * autorización"); lo demás, supervisor para arriba. El backend lo vuelve a
 * controlar igual.
 */
@Component({
    selector: 'app-discount-panel',
    imports: [DecimalPipe, FormsModule, IconComponent],
    template: `
    @if (order(); as o) {
      <div class="panel">
        @for (d of applied(); track d.id) {
          <div class="applied">
            <app-icon name="percent" [size]="16" />
            <span class="applied-text">
              <strong>{{ d.name }}</strong>
              <span class="muted small">{{ targetLabel(d) }}@if (d.reason) { · {{ d.reason }} }</span>
            </span>
            <span class="money applied-amount">− $ {{ d.amount | number: '1.2-2' }}</span>
            @if (!d.supervisorOnly || isSupervisor()) {
              <button type="button" class="btn btn-ghost btn-sm" [disabled]="saving()" (click)="remove(d)">Quitar</button>
            }
          </div>
        }

        @if (!editing()) {
          @if (canApplyAnything()) {
            <button type="button" class="link-btn" (click)="start()">
              <app-icon name="percent" [size]="15" /> {{ applied().length ? 'Otro descuento' : 'Aplicar descuento' }}
            </button>
          }
        } @else {
          <div class="editor">
            <div class="field">
              <label class="label" for="discount-target">¿A qué?</label>
              <select id="discount-target" [ngModel]="target()" (ngModelChange)="target.set($event)">
                <option value="">Todo el pedido</option>
                @for (item of o.items; track item.id) {
                  <option [value]="item.id">{{ itemLabel(item) }}</option>
                }
              </select>
            </div>

            <div class="field">
              <span class="label">¿Cuál?</span>
              @if (loadingPresets()) {
                <span class="muted small"><span class="spinner"></span> Cargando…</span>
              }
              <div class="chips">
                @for (p of presets(); track p.id) {
                  <button
                    type="button"
                    class="chip"
                    [class.selected]="presetId() === p.id"
                    [disabled]="p.requiresAuthorization && !isSupervisor()"
                    [title]="p.requiresAuthorization && !isSupervisor() ? 'Lo aplica un supervisor' : ''"
                    (click)="choosePreset(p.id)"
                  >
                    @if (p.requiresAuthorization) {
                      <app-icon name="lock" [size]="13" />
                    }
                    {{ p.name }} · {{ valueLabel(p) }}
                  </button>
                }
                @if (isSupervisor()) {
                  <button type="button" class="chip" [class.selected]="presetId() === 'MANUAL'" (click)="choosePreset('MANUAL')">
                    <app-icon name="edit" [size]="13" /> Otro (a mano)
                  </button>
                }
              </div>
              @if (!isSupervisor()) {
                <span class="muted small">¿Otro descuento? Lo aplica un supervisor, administrador o dueño.</span>
              }
            </div>

            @if (presetId() === 'MANUAL') {
              <div class="manual">
                <div class="segmented">
                  <button type="button" [class.active]="manualType() === 'PERCENTAGE'" (click)="manualType.set('PERCENTAGE')">%</button>
                  <button type="button" [class.active]="manualType() === 'FIXED_AMOUNT'" (click)="manualType.set('FIXED_AMOUNT')">$</button>
                </div>
                <input
                  type="number"
                  inputmode="decimal"
                  min="0"
                  step="0.01"
                  [placeholder]="manualType() === 'PERCENTAGE' ? 'Ej.: 15' : 'Ej.: 500'"
                  aria-label="Valor del descuento"
                  [ngModel]="manualValue()"
                  (ngModelChange)="manualValue.set($event === '' || $event === null ? null : +$event)"
                />
              </div>
              <input
                type="text"
                maxlength="255"
                placeholder="Motivo (obligatorio): ej. demora en la cocina"
                aria-label="Motivo"
                [ngModel]="reason()"
                (ngModelChange)="reason.set($event)"
              />
            }

            @if (preview() !== null) {
              <p class="preview">Descuenta <strong class="money">$ {{ preview() | number: '1.2-2' }}</strong></p>
            }
            @if (error(); as err) {
              <p class="field-error">{{ err }}</p>
            }
            <div class="editor-actions">
              <button type="button" class="btn btn-ghost btn-sm" [disabled]="saving()" (click)="cancel()">Cancelar</button>
              <button type="button" class="btn btn-primary btn-sm" [disabled]="saving() || !canSubmit()" (click)="apply()">
                {{ saving() ? 'Aplicando…' : 'Aplicar' }}
              </button>
            </div>
          </div>
        }
      </div>
    }
  `,
    changeDetection: ChangeDetectionStrategy.Eager,
    styles: [
        `
      .panel {
        display: flex;
        flex-direction: column;
        gap: 0.45rem;
        margin-bottom: 1rem;
      }
      .applied {
        display: flex;
        align-items: center;
        gap: 0.55rem;
        padding: 0.45rem 0.65rem;
        border-radius: var(--radius);
        background: var(--success-soft, var(--surface-2));
        color: var(--text);
      }
      .applied app-icon {
        color: var(--success, var(--primary));
        flex: none;
      }
      .applied-text {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
      }
      .applied-amount {
        font-weight: 700;
        white-space: nowrap;
      }
      .link-btn {
        align-self: flex-start;
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        border: none;
        background: none;
        padding: 0.2rem 0;
        color: var(--primary);
        font: inherit;
        font-size: 0.9rem;
        font-weight: 650;
        cursor: pointer;
      }
      .editor {
        display: flex;
        flex-direction: column;
        gap: 0.7rem;
        padding: 0.8rem;
        border: 1px solid var(--border-strong);
        border-radius: var(--radius);
      }
      .chips {
        display: flex;
        flex-wrap: wrap;
        gap: 0.4rem;
      }
      .chips .chip:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
      .manual {
        display: flex;
        gap: 0.5rem;
        align-items: center;
      }
      .manual input {
        flex: 1;
      }
      .preview {
        margin: 0;
        font-size: 0.9rem;
      }
      .editor-actions {
        display: flex;
        justify-content: flex-end;
        gap: 0.4rem;
      }
    `,
    ]
})
export class DiscountPanelComponent {
  private readonly discountService = inject(DiscountService);
  private readonly orderService = inject(OrderService);
  private readonly authService = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly order = input<OrderResponse | null>(null);
  /** Cada cambio devuelve el pedido actualizado (con el total nuevo). */
  readonly updated = output<OrderResponse>();

  readonly presets = signal<DiscountResponse[]>([]);
  readonly loadingPresets = signal(false);
  private presetsLoaded = false;

  readonly editing = signal(false);
  /** '' = todo el pedido; si no, id del ítem. */
  readonly target = signal('');
  /** id de la lista, 'MANUAL' o null. */
  readonly presetId = signal<string | null>(null);
  readonly manualType = signal<DiscountType>('PERCENTAGE');
  readonly manualValue = signal<number | null>(null);
  readonly reason = signal('');
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly isSupervisor = computed(() => SUPERVISOR_ROLES.includes(this.authService.user()?.role ?? ''));

  readonly applied = computed<OrderDiscountResponse[]>(() => this.order()?.discounts ?? []);

  /** Si no hay lista y no es supervisor, no hay nada para aplicar: no se muestra el botón. */
  readonly canApplyAnything = computed(
    () => this.isSupervisor() || this.presets().some((p) => !p.requiresAuthorization),
  );

  private readonly chosen = computed<{ type: DiscountType; value: number } | null>(() => {
    const id = this.presetId();
    if (!id) return null;
    if (id === 'MANUAL') {
      const value = this.manualValue();
      return value && value > 0 ? { type: this.manualType(), value } : null;
    }
    const preset = this.presets().find((p) => p.id === id);
    return preset ? { type: preset.type, value: preset.value } : null;
  });

  /** Aproximado de lo que va a descontar (el número final lo calcula el backend). */
  readonly preview = computed<number | null>(() => {
    const o = this.order();
    const chosen = this.chosen();
    if (!o || !chosen) return null;
    const itemId = this.target();
    let base: number;
    if (itemId) {
      const item = o.items.find((i) => i.id === itemId);
      if (!item) return null;
      base = item.unitPrice * item.quantity;
    } else {
      base = o.subtotal;
    }
    const raw = chosen.type === 'PERCENTAGE' ? Math.round(base * chosen.value) / 100 : chosen.value;
    return Math.max(0, Math.min(raw, base));
  });

  readonly canSubmit = computed(() => {
    if (!this.chosen()) return false;
    if (this.presetId() === 'MANUAL') {
      if (this.manualType() === 'PERCENTAGE' && (this.manualValue() ?? 0) > 100) return false;
      return this.reason().trim().length > 0;
    }
    return true;
  });

  valueLabel = discountValueLabel;

  constructor() {
    // La lista se trae una vez, apenas se abre la ventana de cobro.
    effect(() => {
      if (this.order()) untracked(() => void this.loadPresets());
    });
  }

  private async loadPresets(): Promise<void> {
    if (this.presetsLoaded || this.loadingPresets()) return;
    this.loadingPresets.set(true);
    try {
      this.presets.set((await this.discountService.findAll()).filter((p) => p.isActive));
      this.presetsLoaded = true;
    } catch {
      // Sin lista igual se puede poner uno a mano (si es supervisor).
    } finally {
      this.loadingPresets.set(false);
    }
  }

  itemLabel(item: OrderResponse['items'][number]): string {
    const options = itemOptionsLabel(item);
    return `${item.quantity}× ${item.productName}${options ? ` (${options})` : ''}`;
  }

  targetLabel(discount: OrderDiscountResponse): string {
    if (!discount.orderItemId) return 'A todo el pedido';
    const item = this.order()?.items.find((i) => i.id === discount.orderItemId);
    return item ? this.itemLabel(item) : 'Un producto';
  }

  async start(): Promise<void> {
    this.editing.set(true);
    this.target.set('');
    this.presetId.set(null);
    this.manualValue.set(null);
    this.reason.set('');
    this.error.set(null);
    await this.loadPresets();
  }

  choosePreset(id: string): void {
    this.presetId.set(this.presetId() === id ? null : id);
    this.error.set(null);
  }

  cancel(): void {
    this.editing.set(false);
    this.error.set(null);
  }

  async apply(): Promise<void> {
    const o = this.order();
    const chosen = this.chosen();
    if (!o || !chosen || !this.canSubmit() || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    try {
      const manual = this.presetId() === 'MANUAL';
      const updated = await this.orderService.applyDiscount(o.id, {
        orderItemId: this.target() || null,
        discountId: manual ? null : this.presetId(),
        type: manual ? chosen.type : undefined,
        value: manual ? chosen.value : undefined,
        reason: manual ? this.reason().trim() : null,
      });
      this.editing.set(false);
      this.updated.emit(updated);
      this.toast.success('Descuento aplicado.');
    } catch (error) {
      this.error.set(extractErrorMessage(error, 'No se pudo aplicar el descuento.'));
    } finally {
      this.saving.set(false);
    }
  }

  async remove(discount: OrderDiscountResponse): Promise<void> {
    const o = this.order();
    if (!o || this.saving()) return;
    this.saving.set(true);
    try {
      this.updated.emit(await this.orderService.removeDiscount(o.id, discount.id));
      this.toast.info(`Se quitó "${discount.name}".`);
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo quitar el descuento.'));
    } finally {
      this.saving.set(false);
    }
  }
}
