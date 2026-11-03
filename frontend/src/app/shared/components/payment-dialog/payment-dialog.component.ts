import { CommonModule } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, input, output, signal, untracked, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { toDataURL } from 'qrcode';
import { QrAvailability, QrChargeResponse } from '../../../core/models/mercadopago.model';
import {
  OrderPaymentResponse,
  OrderResponse,
  PAYMENT_METHOD_LABELS,
  PaymentMethod,
  itemOptionsLabel,
  totalDiscounted,
} from '../../../core/models/order.model';
import { extractErrorMessage } from '../../../core/services/error.util';
import { MercadoPagoService } from '../../../core/services/mercadopago.service';
import { OrderService } from '../../../core/services/order.service';
import { PrintContextService } from '../../../core/services/print-context.service';
import { TicketPrintService } from '../../../core/services/ticket-print.service';
import { buildPartTicket } from '../../../core/services/ticket-templates';
import { ToastService } from '../../../core/services/toast.service';
import { DiscountPanelComponent } from '../discount-panel/discount-panel.component';
import { IconComponent, IconName } from '../icon/icon.component';
import { ModalComponent } from '../modal/modal.component';

/**
 * 'MP_QR' = cobro con QR de Mercado Pago integrado (paso 2.2): no es un
 * método más del backend, es un flujo aparte (crea el QR y espera la
 * confirmación). Cuando MP confirma, el cobro queda registrado como 'QR'.
 * El 'QR' común sigue para QR de otras billeteras que se confirman a ojo.
 */
type DialogMethod = PaymentMethod | 'MP_QR';

/** Cada cuánto se le pregunta al backend si el cliente ya pagó el QR. */
const QR_POLL_MS = 3000;

const METHODS: readonly { method: PaymentMethod; icon: IconName }[] = [
  { method: 'CASH', icon: 'cash' },
  { method: 'DEBIT_CARD', icon: 'card' },
  { method: 'CREDIT_CARD', icon: 'card' },
  { method: 'TRANSFER', icon: 'transfer' },
  { method: 'QR', icon: 'qr' },
  { method: 'OTHER', icon: 'more' },
];

/** Billetes "redondos" con los que suele pagar la gente — para las sugerencias de "¿con cuánto paga?". */
const ROUND_STEPS = [1000, 2000, 5000, 10000, 20000];

/**
 * Cobro de un pedido, en una sola ventana y con el menor tipeo posible:
 *  1. Tocás cómo paga (efectivo, débito, QR…).
 *  2. El monto ya viene cargado con lo que falta cobrar.
 *  3. Si es efectivo, tocás con qué billete paga y te dice el vuelto.
 *  4. "Cobrar". Si pagó una parte con un método y otra con otro (pago
 *     dividido), la ventana queda abierta con lo que falta.
 *
 * Con Mercado Pago conectado (paso 2.2) aparece además "QR Mercado Pago":
 * se genera el QR (o se carga el monto en el QR impreso del mostrador), la
 * ventana espera y, cuando el cliente paga, el cobro se registra solo.
 *
 * Mismas reglas que el backend (OrderService#addPayment): nunca más que el
 * saldo, y hace falta la caja abierta. El "con cuánto paga" es solo para
 * calcular el vuelto: al backend viaja el monto cobrado, no el billete.
 */
@Component({
    selector: 'app-payment-dialog',
    imports: [CommonModule, FormsModule, RouterLink, IconComponent, ModalComponent, DiscountPanelComponent],
    template: `
    <app-modal
      [open]="!!live()"
      [title]="'Cobrar · ' + tableLabel()"
      [subtitle]="subtitle()"
      [dismissable]="!submitting()"
      size="narrow"
      (closed)="close()"
    >
      @if (live(); as o) {
        <div class="summary">
          @if (discounted() > 0) {
            <div class="summary-row">
              <span>Descuentos</span>
              <span class="money">− $ {{ discounted() | number: '1.2-2' }}</span>
            </div>
          }
          <div class="summary-row">
            <span>Total del pedido</span>
            <span class="money">$ {{ o.total | number: '1.2-2' }}</span>
          </div>
          @if (o.paidTotal > 0 && !fullyPaid()) {
            <div class="summary-row">
              <span>Ya cobrado</span>
              <span class="money">$ {{ o.paidTotal | number: '1.2-2' }}</span>
            </div>
          }
          <div class="summary-due" [class.paid]="fullyPaid()">
            <span>{{ fullyPaid() ? 'Cobrado' : 'Falta cobrar' }}</span>
            <strong class="money">$ {{ (fullyPaid() ? o.total : o.balanceDue) | number: '1.2-2' }}</strong>
          </div>
        </div>

        @if (fullyPaid()) {
          <div class="done">
            <span class="done-icon"><app-icon name="check" [size]="30" [strokeWidth]="2.5" /></span>
            <h3>¡Pedido cobrado!</h3>
            @if (lastChange() > 0) {
              <p class="change-done">Vuelto: <strong class="money">$ {{ lastChange() | number: '1.2-2' }}</strong></p>
            }
            <div class="done-payments">
              @for (payment of o.payments; track payment.id) {
                <span class="badge">{{ methodLabels[payment.paymentMethod] }} $ {{ payment.amount | number: '1.2-2' }}</span>
              }
            </div>
            @if (itemPayments().length > 0) {
              <div class="part-tickets">
                <span class="muted small">Ticket de cada persona:</span>
                @for (payment of itemPayments(); track payment.id; let i = $index) {
                  <button type="button" class="btn btn-ghost btn-sm" (click)="printPart(payment)">
                    <app-icon name="printer" [size]="15" /> Persona {{ i + 1 }} · $ {{ payment.amount | number: '1.2-2' }}
                  </button>
                }
              </div>
            }
          </div>
        } @else if (charge()) {
          @if (charge(); as c) {
          <div class="qr-panel" aria-live="polite">
            @if (c.mode === 'DYNAMIC') {
              @if (qrImage(); as img) {
                <img class="qr-image" [src]="img" alt="Código QR de Mercado Pago para pagar" />
              } @else {
                <div class="qr-image qr-placeholder"><span class="spinner"></span></div>
              }
              <p class="qr-instructions">Mostrale este QR al cliente para que lo escanee con Mercado Pago o la app de su banco.</p>
            } @else {
              <span class="qr-static-icon"><app-icon name="qr" [size]="44" /></span>
              <p class="qr-instructions">
                Cargamos el monto en el <strong>QR del mostrador</strong>. Pedile al cliente que lo escanee con Mercado Pago o la app de su banco.
              </p>
            }
            <div class="qr-amount money">$ {{ c.amount | number: '1.2-2' }}</div>
            <div class="qr-waiting">
              <span class="spinner"></span> Esperando el pago…
              @if (minutesLeft() !== null) {
                <span class="muted">· vence en {{ minutesLeft() }} min</span>
              }
            </div>
            @if (mpAvailability()?.testMode) {
              <span class="badge badge-warning">Modo prueba: no se cobra plata real</span>
            }
            @if (error(); as err) {
              <div class="alert alert-danger">
                <app-icon name="alert" [size]="18" />
                <div class="alert-body">{{ err }}</div>
              </div>
            }
          </div>
          }
        } @else {
          <app-discount-panel [order]="o" (updated)="onDiscountChanged($event)" />
          <div class="stack">
            <div class="stack-sm">
              <span class="label">¿Cómo pagan?</span>
              <div class="segmented block split-modes" role="radiogroup" aria-label="Dividir la cuenta">
                <button type="button" [class.active]="split() === 'ALL'" (click)="setSplit('ALL')">Todo junto</button>
                <button type="button" [class.active]="split() === 'EQUAL'" (click)="setSplit('EQUAL')">Partes iguales</button>
                <button type="button" [class.active]="split() === 'ITEMS'" (click)="setSplit('ITEMS')">Por productos</button>
              </div>
            </div>

            @if (split() === 'EQUAL') {
              <div class="split-box">
                <span class="label">¿Entre cuántos quedan por pagar?</span>
                <div class="row quick">
                  @for (n of peopleOptions; track n) {
                    <button type="button" class="chip" [class.selected]="people() === n" (click)="setPeople(n)">{{ n }}</button>
                  }
                </div>
                <p class="split-share">
                  Cada uno: <strong class="money">$ {{ equalShare() | number: '1.2-2' }}</strong>
                  @if (people() > 1) {
                    <span class="muted small">· el último paga lo que falte, así no quedan centavos</span>
                  }
                </p>
              </div>
            }

            @if (split() === 'ITEMS') {
              <div class="split-box">
                <span class="label">¿Qué paga esta persona?</span>
                <ul class="split-items">
                  @for (item of o.items; track item.id) {
                    <li [class.paid]="remainingOf(item) === 0">
                      <span class="split-name">
                        <strong>{{ item.quantity }}× {{ item.productName }}</strong>
                        @if (optionsOf(item); as opts) {
                          <span class="muted small">{{ opts }}</span>
                        }
                        @if ((item.paidQuantity ?? 0) > 0) {
                          <span class="paid-note small">
                            <app-icon name="check" [size]="12" [strokeWidth]="3" />
                            {{ remainingOf(item) === 0 ? 'Pagado' : 'Pagados ' + item.paidQuantity + ' de ' + item.quantity }}
                          </span>
                        }
                      </span>
                      @if (remainingOf(item) > 0) {
                        <div class="stepper-qty">
                          <button type="button" class="qty-btn" [disabled]="picked(item.id) === 0" (click)="pick(item.id, -1)" aria-label="Uno menos">
                            <app-icon name="minus" [size]="16" />
                          </button>
                          <span class="qty-value">{{ picked(item.id) }}</span>
                          <button type="button" class="qty-btn" [disabled]="picked(item.id) >= remainingOf(item)" (click)="pick(item.id, 1)" aria-label="Uno más">
                            <app-icon name="plus" [size]="16" />
                          </button>
                        </div>
                      }
                    </li>
                  }
                </ul>
                <div class="row quick">
                  <button type="button" class="chip" (click)="pickAllRemaining()">Todo lo que falta</button>
                  @if (hasPicks()) {
                    <button type="button" class="chip" (click)="clearPicks()">Limpiar</button>
                  }
                </div>
                <p class="split-share">
                  Esta persona paga: <strong class="money">$ {{ itemsAmount() | number: '1.2-2' }}</strong>
                </p>
              </div>
            }

            @if (lastPart(); as part) {
              <div class="part-done">
                <app-icon name="check" [size]="16" />
                <span>Cobrado $ {{ part.amount | number: '1.2-2' }} a esta persona.</span>
                <button type="button" class="btn btn-ghost btn-sm" (click)="printPart(part)"><app-icon name="printer" [size]="15" /> Su ticket</button>
              </div>
            }

            @if (mpAvailable()) {
              <button
                type="button"
                class="method method-mp"
                role="radio"
                [attr.aria-checked]="method() === 'MP_QR'"
                [class.selected]="method() === 'MP_QR'"
                (click)="selectMethod('MP_QR')"
              >
                <app-icon name="qr" [size]="24" />
                <span class="method-mp-text">
                  <strong>QR Mercado Pago</strong>
                  <small>{{ mpAvailability()?.mode === 'STATIC' ? 'Se carga en el QR del mostrador' : 'El cliente escanea y se confirma solo' }}</small>
                </span>
              </button>
            }
            <div class="stack-sm">
              <span class="label">1. ¿Cómo paga?</span>
              <div class="method-grid" role="radiogroup" aria-label="Método de pago">
                @for (m of methods; track m.method) {
                  <button
                    type="button"
                    class="method"
                    role="radio"
                    [attr.aria-checked]="method() === m.method"
                    [class.selected]="method() === m.method"
                    (click)="selectMethod(m.method)"
                  >
                    <app-icon [name]="m.icon" [size]="22" />
                    <span>{{ methodLabels[m.method] }}</span>
                  </button>
                }
              </div>
            </div>

            @if (split() !== 'ITEMS') {
            <div class="stack-sm">
              <label class="label" for="pay-amount">2. ¿Cuánto cobrás con {{ methodLabel() }}?</label>
              <div class="input-money">
                <input
                  id="pay-amount"
                  type="number"
                  inputmode="decimal"
                  step="0.01"
                  min="0"
                  [ngModel]="amount()"
                  (ngModelChange)="amount.set(+$event || 0)"
                />
              </div>
              <div class="row quick">
                <button type="button" class="chip" [class.selected]="amount() === o.balanceDue" (click)="amount.set(o.balanceDue)">
                  Todo lo que falta
                </button>
                <button type="button" class="chip" (click)="amount.set(half(o.balanceDue))">La mitad</button>
              </div>
              @if (amount() > 0 && amount() < o.balanceDue - 0.001) {
                <p class="help-line">
                  <app-icon name="info" [size]="16" /> Van a quedar $ {{ o.balanceDue - amount() | number: '1.2-2' }} para cobrar
                  {{ split() === 'EQUAL' ? 'entre los demás' : 'con otro método' }}.
                </p>
              }
            </div>
            }

            @if (method() === 'CASH') {
              <div class="stack-sm cash-box">
                <label class="label" for="pay-received">3. ¿Con cuánto paga? <span class="muted">(para el vuelto)</span></label>
                <div class="row quick">
                  @for (bill of receivedSuggestions(); track bill) {
                    <button type="button" class="chip" [class.selected]="received() === bill" (click)="received.set(bill)">
                      $ {{ bill | number: '1.0-0' }}
                    </button>
                  }
                </div>
                <div class="input-money">
                  <input
                    id="pay-received"
                    type="number"
                    inputmode="decimal"
                    step="0.01"
                    min="0"
                    placeholder="Otro monto"
                    [ngModel]="received()"
                    (ngModelChange)="received.set($event === '' || $event === null ? null : +$event)"
                  />
                </div>
                @if (received() !== null) {
                  @if (change() >= 0) {
                    <div class="change">
                      <span>Vuelto</span>
                      <strong class="money">$ {{ change() | number: '1.2-2' }}</strong>
                    </div>
                  } @else {
                    <p class="field-error">Con eso no alcanza: faltan $ {{ -change() | number: '1.2-2' }}.</p>
                  }
                }
              </div>
            }

            @if (error(); as err) {
              <div class="alert alert-danger">
                <app-icon name="alert" [size]="18" />
                <div class="alert-body">
                  {{ err }}
                  @if (needsCashSession()) {
                    <a routerLink="/caja" (click)="close()">Ir a Caja para abrirla</a>
                  }
                </div>
              </div>
            }
          </div>
        }
      }

      <div modal-footer>
        @if (fullyPaid()) {
          <button type="button" class="btn btn-secondary btn-lg" (click)="printRequested.emit(live()!)">
            <app-icon name="printer" /> Ticket
          </button>
          <button type="button" class="btn btn-primary btn-lg" (click)="close()">Listo</button>
        } @else if (charge()) {
          <button type="button" class="btn btn-secondary btn-lg btn-block" [disabled]="cancelling()" (click)="cancelQrCharge()">
            @if (cancelling()) {
              <span class="spinner"></span> Cancelando…
            } @else {
              <app-icon name="x" /> Cancelar el cobro con QR
            }
          </button>
        } @else {
          <button
            type="button"
            class="btn btn-success btn-lg btn-block"
            [disabled]="submitting() || !canSubmit()"
            (click)="submit()"
          >
            @if (submitting()) {
              <span class="spinner"></span> {{ method() === 'MP_QR' ? 'Generando el QR…' : 'Cobrando…' }}
            } @else if (method() === 'MP_QR') {
              <app-icon name="qr" /> Cobrar $ {{ amount() | number: '1.2-2' }} con QR
            } @else {
              <app-icon name="check" /> Cobrar $ {{ amount() | number: '1.2-2' }}
            }
          </button>
        }
      </div>
    </app-modal>
  `,
    changeDetection: ChangeDetectionStrategy.Eager,
    styles: [
        `
      .summary {
        display: flex;
        flex-direction: column;
        gap: 0.3rem;
        padding: 0.85rem 1rem;
        border-radius: var(--radius);
        background: var(--surface-2);
        margin-bottom: 1rem;
      }
      .summary-row {
        display: flex;
        justify-content: space-between;
        color: var(--text-muted);
        font-size: 0.9rem;
      }
      .summary-due {
        display: flex;
        justify-content: space-between;
        align-items: baseline;
        font-weight: 650;
        padding-top: 0.3rem;
        border-top: 1px solid var(--border);
        margin-top: 0.2rem;
      }
      .summary-due strong {
        font-size: 1.6rem;
        color: var(--primary);
      }
      .summary-due.paid strong {
        color: var(--success);
      }
      .method-grid {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 0.5rem;
      }
      .method {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 0.3rem;
        padding: 0.7rem 0.3rem;
        min-height: 68px;
        border-radius: var(--radius);
        border: 1.5px solid var(--border-strong);
        background: var(--surface);
        color: var(--text);
        font: inherit;
        font-size: 0.82rem;
        font-weight: 600;
        cursor: pointer;
        transition:
          border-color var(--transition),
          background var(--transition);
      }
      .method:hover {
        background: var(--surface-2);
      }
      .method.selected {
        border-color: var(--primary);
        background: var(--primary-soft);
        color: var(--primary);
      }
      .quick {
        gap: 0.4rem;
      }
      .quick .chip {
        min-height: 36px;
        font-size: 0.85rem;
      }
      .help-line {
        display: flex;
        gap: 0.4rem;
        align-items: center;
        color: var(--info);
        font-size: 0.85rem;
      }
      .cash-box {
        padding: 0.85rem;
        border-radius: var(--radius);
        border: 1px dashed var(--border-strong);
      }
      .change {
        display: flex;
        justify-content: space-between;
        align-items: baseline;
        padding: 0.6rem 0.85rem;
        border-radius: var(--radius-sm);
        background: var(--success-soft);
        color: var(--success);
        font-weight: 650;
      }
      .change strong {
        font-size: 1.4rem;
      }
      .done {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 0.5rem;
        text-align: center;
        padding: 0.75rem 0 0.25rem;
      }
      .done-icon {
        width: 60px;
        height: 60px;
        border-radius: 50%;
        background: var(--success-soft);
        color: var(--success);
        display: flex;
        align-items: center;
        justify-content: center;
      }
      .change-done strong {
        font-size: 1.3rem;
        color: var(--success);
      }
      .done-payments {
        display: flex;
        gap: 0.4rem;
        flex-wrap: wrap;
        justify-content: center;
      }
      .method-mp {
        flex-direction: row;
        justify-content: flex-start;
        gap: 0.75rem;
        width: 100%;
        min-height: 60px;
        padding: 0.7rem 1rem;
        border-color: #00b1ea;
        text-align: left;
      }
      .method-mp.selected {
        background: rgba(0, 177, 234, 0.12);
        border-color: #009ee3;
        color: inherit;
      }
      .method-mp-text {
        display: flex;
        flex-direction: column;
        gap: 0.1rem;
      }
      .method-mp-text strong {
        font-size: 0.95rem;
      }
      .method-mp-text small {
        font-weight: 500;
        color: var(--text-muted);
      }
      .qr-panel {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 0.6rem;
        text-align: center;
      }
      .qr-image {
        width: min(260px, 70vw);
        height: min(260px, 70vw);
        border-radius: var(--radius);
        background: #fff;
        padding: 0.5rem;
        border: 1px solid var(--border);
        image-rendering: pixelated;
      }
      .qr-placeholder {
        display: flex;
        align-items: center;
        justify-content: center;
      }
      .qr-static-icon {
        width: 76px;
        height: 76px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        background: rgba(0, 177, 234, 0.12);
        color: #009ee3;
      }
      .qr-instructions {
        margin: 0;
        max-width: 32ch;
        color: var(--text-muted);
      }
      .qr-amount {
        font-size: 1.8rem;
        font-weight: 700;
      }
      .qr-waiting {
        display: flex;
        align-items: center;
        gap: 0.4rem;
        font-weight: 600;
      }
      [modal-footer] {
        display: flex;
        gap: 0.5rem;
        width: 100%;
      }
      [modal-footer] .btn {
        flex: 1;
      }
      .split-modes {
        overflow: hidden;
      }
      .split-modes button {
        padding: 0 0.35rem;
        font-size: 0.86rem;
        min-width: 0;
      }
      .split-box {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
        padding: 0.75rem;
        border: 1px solid var(--border);
        border-radius: var(--radius);
      }
      .split-share {
        margin: 0;
      }
      .split-items {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
      }
      .split-items li {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.35rem 0;
        border-bottom: 1px solid var(--border);
      }
      .split-items li:last-child {
        border-bottom: none;
      }
      .split-items li.paid {
        opacity: 0.6;
      }
      .split-name {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
      }
      .paid-note {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        color: var(--success);
        font-weight: 650;
      }
      .stepper-qty {
        display: flex;
        align-items: center;
        border: 1px solid var(--border-strong);
        border-radius: var(--radius-full);
      }
      .qty-btn {
        width: 36px;
        height: 36px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border: none;
        background: none;
        color: var(--text);
        cursor: pointer;
        border-radius: 50%;
      }
      .qty-btn:disabled {
        opacity: 0.35;
        cursor: not-allowed;
      }
      .qty-value {
        min-width: 1.5rem;
        text-align: center;
        font-weight: 700;
      }
      .part-done {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.5rem 0.75rem;
        border-radius: var(--radius);
        background: var(--success-soft);
        color: var(--text);
        font-size: 0.9rem;
      }
      .part-done span {
        flex: 1;
      }
      .part-done app-icon {
        color: var(--success);
      }
      .part-tickets {
        display: flex;
        flex-wrap: wrap;
        justify-content: center;
        align-items: center;
        gap: 0.35rem;
        margin-top: 0.5rem;
      }
    `,
    ]
})
export class PaymentDialogComponent {
  private readonly orderService = inject(OrderService);
  private readonly mercadoPago = inject(MercadoPagoService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly printContext = inject(PrintContextService);
  private readonly ticketPrint = inject(TicketPrintService);

  /** Pedido a cobrar; `null` = ventana cerrada. */
  readonly order = input<OrderResponse | null>(null);
  readonly tableLabel = input<string>('');
  readonly closed = output<void>();
  /** Cada cobro registrado devuelve el pedido actualizado (con saldo nuevo). */
  readonly orderUpdated = output<OrderResponse>();
  readonly printRequested = output<OrderResponse>();

  readonly methods = METHODS;
  readonly methodLabels = PAYMENT_METHOD_LABELS;

  /** Copia local que se actualiza con la respuesta de cada cobro (pago dividido). */
  readonly live = signal<OrderResponse | null>(null);
  readonly method = signal<DialogMethod>('CASH');
  readonly amount = signal(0);
  readonly received = signal<number | null>(null);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly lastChange = signal(0);

  // --- QR de Mercado Pago ---
  /** Si en el local del pedido se puede cobrar con QR de Mercado Pago (null = todavía no se sabe o falló). */
  readonly mpAvailability = signal<QrAvailability | null>(null);
  readonly mpAvailable = computed(() => !!this.mpAvailability()?.available);
  /** Cobro con QR esperando el pago (mientras hay uno, la ventana muestra el QR). */
  readonly charge = signal<QrChargeResponse | null>(null);
  readonly qrImage = signal<string | null>(null);
  readonly cancelling = signal(false);
  private readonly now = signal(Date.now());
  private pollTimer: ReturnType<typeof setInterval> | null = null;
  private polling = false;

  readonly minutesLeft = computed(() => {
    const c = this.charge();
    if (!c) return null;
    const ms = new Date(c.expiresAt).getTime() - this.now();
    return ms > 0 ? Math.max(1, Math.ceil(ms / 60000)) : null;
  });

  readonly methodLabel = computed(() => {
    const m = this.method();
    // "QR de Mercado Pago" conserva las mayúsculas; el resto va en minúscula ("con efectivo").
    return m === 'MP_QR' ? 'QR de Mercado Pago' : this.methodLabels[m].toLowerCase();
  });

  readonly fullyPaid = computed(() => {
    const o = this.live();
    return !!o && o.balanceDue <= 0.001;
  });

  readonly subtitle = computed(() => {
    const o = this.live();
    if (!o) return '';
    const items = o.items.reduce((sum, item) => sum + item.quantity, 0);
    return `Pedido #${o.id.slice(0, 8).toUpperCase()} · ${items} ${items === 1 ? 'ítem' : 'ítems'}`;
  });

  readonly change = computed(() => (this.received() ?? 0) - this.amount());

  // --- V14: dividir la cuenta ---
  readonly peopleOptions = [2, 3, 4, 5, 6, 8];
  readonly split = signal<'ALL' | 'EQUAL' | 'ITEMS'>('ALL');
  /** Partes iguales: cuántas personas faltan pagar (baja sola con cada cobro). */
  readonly people = signal(2);
  /** Por productos: unidades elegidas de cada renglón para ESTA persona. */
  readonly picks = signal<Record<string, number>>({});
  /** Último cobro "por productos" hecho en esta ventana (para imprimir su ticket). */
  readonly lastPart = signal<OrderPaymentResponse | null>(null);

  readonly equalShare = computed(() => {
    const o = this.live();
    if (!o) return 0;
    const n = Math.max(1, this.people());
    return n === 1 ? o.balanceDue : Math.round((o.balanceDue / n) * 100) / 100;
  });

  readonly hasPicks = computed(() => Object.values(this.picks()).some((q) => q > 0));

  /** Mismo cálculo que el backend (OrderService#buildPaidLines); el número final lo pone el backend. */
  readonly itemsAmount = computed(() => {
    const o = this.live();
    if (!o) return 0;
    const picks = this.picks();
    const factor = o.subtotal > 0 ? o.total / o.subtotal : 0;
    let sum = 0;
    let coversAll = true;
    for (const item of o.items) {
      const q = picks[item.id] ?? 0;
      if (q > 0) sum += Math.round(((item.subtotal * q) / item.quantity) * factor * 100) / 100;
      if ((item.paidQuantity ?? 0) + q < item.quantity) coversAll = false;
    }
    if (sum <= 0) return 0;
    return coversAll ? o.balanceDue : Math.min(sum, o.balanceDue);
  });

  readonly itemPayments = computed(() => (this.live()?.payments ?? []).filter((p) => (p.items ?? []).length > 0));

  /** V12 — todo lo descontado (por producto + sobre el pedido). */
  readonly discounted = computed(() => {
    const o = this.live();
    return o ? totalDiscounted(o) : 0;
  });

  readonly receivedSuggestions = computed(() => {
    const amount = this.amount();
    if (amount <= 0) return [];
    const options = new Set<number>([Math.round(amount * 100) / 100]);
    for (const step of ROUND_STEPS) {
      const rounded = Math.ceil(amount / step) * step;
      if (rounded > amount) options.add(rounded);
      if (options.size >= 4) break;
    }
    return [...options].sort((a, b) => a - b);
  });

  readonly needsCashSession = computed(() => (this.error() ?? '').toLowerCase().includes('caja'));

  readonly canSubmit = computed(() => {
    const o = this.live();
    if (this.split() === 'ITEMS') {
      if (!o || !this.hasPicks() || this.itemsAmount() <= 0) return false;
      if (this.method() === 'CASH' && this.received() !== null && this.change() < 0) return false;
      return true;
    }
    const amount = this.amount();
    if (!o || amount <= 0 || amount > o.balanceDue + 0.001) return false;
    if (this.method() === 'CASH' && this.received() !== null && this.change() < 0) return false;
    return true;
  });

  constructor() {
    this.destroyRef.onDestroy(() => this.stopPolling());
    effect(() => {
      const incoming = this.order();
      untracked(() => {
        const current = this.live();
        // Nuevo pedido abierto (o se cerró): arranca de cero.
        if (!incoming || !current || incoming.id !== current.id) {
          this.stopPolling();
          this.live.set(incoming);
          this.method.set('CASH');
          this.amount.set(incoming ? Math.max(0, incoming.balanceDue) : 0);
          this.received.set(null);
          this.error.set(null);
          this.lastChange.set(0);
          this.split.set('ALL');
          this.people.set(2);
          this.picks.set({});
          this.lastPart.set(null);
          this.charge.set(null);
          this.qrImage.set(null);
          this.mpAvailability.set(null);
          if (incoming) void this.loadMpAvailability(incoming.locationId);
        }
      });
    });
  }

  // ---------------------------------------------------------------------
  // V14 — dividir la cuenta
  // ---------------------------------------------------------------------

  setSplit(mode: 'ALL' | 'EQUAL' | 'ITEMS'): void {
    const o = this.live();
    this.split.set(mode);
    this.picks.set({});
    this.received.set(null);
    this.error.set(null);
    if (!o) return;
    if (mode === 'ALL') this.amount.set(Math.max(0, o.balanceDue));
    if (mode === 'EQUAL') this.amount.set(this.equalShare());
    if (mode === 'ITEMS') this.amount.set(0);
  }

  setPeople(n: number): void {
    this.people.set(n);
    this.amount.set(this.equalShare());
    this.received.set(null);
  }

  remainingOf(item: OrderResponse['items'][number]): number {
    return Math.max(0, item.quantity - (item.paidQuantity ?? 0));
  }

  optionsOf(item: OrderResponse['items'][number]): string {
    return itemOptionsLabel(item);
  }

  picked(itemId: string): number {
    return this.picks()[itemId] ?? 0;
  }

  pick(itemId: string, delta: 1 | -1): void {
    const item = this.live()?.items.find((i) => i.id === itemId);
    if (!item) return;
    const next = Math.min(this.remainingOf(item), Math.max(0, this.picked(itemId) + delta));
    this.picks.set({ ...this.picks(), [itemId]: next });
    this.amount.set(this.itemsAmount());
    this.received.set(null);
  }

  pickAllRemaining(): void {
    const o = this.live();
    if (!o) return;
    const all: Record<string, number> = {};
    for (const item of o.items) all[item.id] = this.remainingOf(item);
    this.picks.set(all);
    this.amount.set(this.itemsAmount());
  }

  private pickedItems(): { orderItemId: string; quantity: number }[] {
    return Object.entries(this.picks())
      .filter(([, quantity]) => quantity > 0)
      .map(([orderItemId, quantity]) => ({ orderItemId, quantity }));
  }

  clearPicks(): void {
    this.picks.set({});
    this.amount.set(0);
  }

  async printPart(payment: OrderPaymentResponse): Promise<void> {
    const o = this.live();
    if (!o) return;
    try {
      const context = await this.printContext.get(o.locationId);
      this.ticketPrint.print(buildPartTicket(o, payment, this.tableLabel(), context));
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo imprimir.'));
    }
  }

  /** Se aplicó o quitó un descuento: el total cambió, el monto a cobrar pasa a ser el saldo nuevo. */
  onDiscountChanged(updated: OrderResponse): void {
    this.live.set(updated);
    this.orderUpdated.emit(updated);
    // Recalcula el monto según cómo se esté dividiendo (todo, partes o productos).
    this.setSplit(this.split());
  }

  selectMethod(method: DialogMethod): void {
    this.method.set(method);
    this.received.set(null);
    this.error.set(null);
  }

  half(value: number): number {
    return Math.round((value / 2) * 100) / 100;
  }

  async submit(): Promise<void> {
    const o = this.live();
    if (!o || !this.canSubmit() || this.submitting()) return;
    if (this.method() === 'MP_QR') {
      await this.startQrCharge(o);
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const change = this.method() === 'CASH' && this.received() !== null ? Math.max(0, this.change()) : 0;
    try {
      const byItems = this.split() === 'ITEMS';
      const updated = await this.orderService.addPayment(
        o.id,
        byItems
          ? {
              paymentMethod: this.method() as PaymentMethod,
              items: this.pickedItems(),
            }
          : { paymentMethod: this.method() as PaymentMethod, amount: this.amount() },
      );
      this.live.set(updated);
      this.orderUpdated.emit(updated);
      this.lastChange.set(change);
      this.received.set(null);
      if (byItems) {
        const newest = [...updated.payments].reverse().find((p) => (p.items ?? []).length > 0) ?? null;
        this.lastPart.set(newest);
        this.picks.set({});
        if (updated.balanceDue > 0.001) {
          this.amount.set(0);
          this.toast.info(`Cobrado. Faltan $ ${updated.balanceDue.toLocaleString('es-AR', { minimumFractionDigits: 2 })}.`);
        } else {
          this.toast.success('Pedido cobrado.');
        }
        return;
      }
      if (this.split() === 'EQUAL' && updated.balanceDue > 0.001) {
        // Una persona menos por pagar; se carga la parte de la siguiente.
        this.people.set(Math.max(1, this.people() - 1));
        this.amount.set(this.equalShare());
        this.toast.info(`Cobrado. Faltan $ ${updated.balanceDue.toLocaleString('es-AR', { minimumFractionDigits: 2 })}.`);
        return;
      }
      if (updated.balanceDue > 0.001) {
        // Pago dividido: queda cargado el resto, y se sugiere otro método.
        this.amount.set(updated.balanceDue);
        this.method.set(this.method() === 'CASH' ? 'DEBIT_CARD' : 'CASH');
        this.toast.info(`Cobro registrado. Faltan $ ${updated.balanceDue.toLocaleString('es-AR', { minimumFractionDigits: 2 })}.`);
      } else {
        this.toast.success('Pedido cobrado.');
      }
    } catch (error) {
      this.error.set(extractErrorMessage(error, 'No se pudo registrar el cobro.'));
    } finally {
      this.submitting.set(false);
    }
  }

  close(): void {
    if (this.submitting()) return;
    if (this.charge()?.status === 'PENDING') {
      // El cobro sigue vivo en Mercado Pago: si el cliente paga, el backend
      // lo registra solo (proceso de fondo). Al volver a abrir y elegir
      // "QR Mercado Pago" aparece el mismo QR.
      this.toast.info('El QR sigue esperando el pago. Si el cliente paga, el cobro se registra solo.');
    }
    this.stopPolling();
    this.charge.set(null);
    this.qrImage.set(null);
    this.live.set(null);
    this.closed.emit();
  }

  // ---------------------------------------------------------------------
  // QR de Mercado Pago
  // ---------------------------------------------------------------------

  async cancelQrCharge(): Promise<void> {
    const c = this.charge();
    if (!c || this.cancelling()) return;
    this.cancelling.set(true);
    this.error.set(null);
    try {
      this.applyCharge(await this.mercadoPago.cancelCharge(c.id));
    } catch (error) {
      this.error.set(extractErrorMessage(error, 'No se pudo cancelar el cobro con QR.'));
    } finally {
      this.cancelling.set(false);
    }
  }

  private async loadMpAvailability(locationId: string): Promise<void> {
    try {
      const availability = await this.mercadoPago.availability(locationId);
      // Solo si sigue abierta la ventana del mismo local.
      if (this.live()?.locationId === locationId) this.mpAvailability.set(availability);
    } catch {
      // Sin datos de Mercado Pago simplemente no se ofrece el QR; el resto del cobro anda igual.
    }
  }

  private async startQrCharge(o: OrderResponse): Promise<void> {
    this.submitting.set(true);
    this.error.set(null);
    try {
      if (this.split() === 'ITEMS') {
        this.applyCharge(await this.mercadoPago.createCharge(o.id, undefined, this.pickedItems()));
        return;
      }
      const fullBalance = Math.abs(this.amount() - o.balanceDue) < 0.001;
      this.applyCharge(await this.mercadoPago.createCharge(o.id, fullBalance ? undefined : this.amount()));
    } catch (error) {
      this.error.set(extractErrorMessage(error, 'No se pudo generar el cobro con QR.'));
    } finally {
      this.submitting.set(false);
    }
  }

  /** Refleja el estado de un cobro con QR: sigue esperando, se pagó, venció o se canceló. */
  private applyCharge(c: QrChargeResponse): void {
    if (!this.live() || this.live()!.id !== c.orderId) return; // la ventana ya muestra otro pedido
    this.live.set(c.order);
    this.orderUpdated.emit(c.order);

    this.now.set(Date.now());
    if (c.status === 'PENDING') {
      const previous = this.charge();
      this.charge.set(c);
      if (c.qrData && previous?.qrData !== c.qrData) void this.renderQr(c.qrData);
      this.startPolling();
      return;
    }

    this.stopPolling();
    this.charge.set(null);
    this.qrImage.set(null);
    switch (c.status) {
      case 'PAID':
        this.lastChange.set(0);
        if (this.split() === 'ITEMS') {
          // Cuenta dividida por productos: esa persona ya pagó; queda su ticket a mano.
          this.lastPart.set([...c.order.payments].reverse().find((p) => (p.items ?? []).length > 0) ?? null);
          this.picks.set({});
          this.amount.set(0);
          this.method.set('CASH');
          if (c.order.balanceDue > 0.001) {
            this.toast.info(
              `Pago con QR confirmado. Faltan $ ${c.order.balanceDue.toLocaleString('es-AR', { minimumFractionDigits: 2 })}.`,
            );
          } else {
            this.toast.success('Pago con QR confirmado. Pedido cobrado.');
          }
          break;
        }
        if (c.order.balanceDue > 0.001) {
          this.amount.set(c.order.balanceDue);
          this.method.set('CASH');
          this.toast.info(
            `Pago con QR confirmado. Faltan $ ${c.order.balanceDue.toLocaleString('es-AR', { minimumFractionDigits: 2 })}.`,
          );
        } else {
          this.toast.success('Pago con QR confirmado. Pedido cobrado.');
        }
        break;
      case 'CANCELLED':
        // Lo canceló quien cobra (o desde otra pantalla): no es un error.
        this.toast.info('Cobro con QR cancelado.');
        break;
      case 'PAID_UNAPPLIED':
        this.error.set(c.statusMessage ?? 'Mercado Pago cobró, pero no se pudo registrar solo. Registralo a mano.');
        break;
      default:
        this.error.set(c.statusMessage ?? 'El cobro con QR no se completó.');
    }
  }

  private async renderQr(data: string): Promise<void> {
    try {
      this.qrImage.set(await toDataURL(data, { width: 320, margin: 1, errorCorrectionLevel: 'M' }));
    } catch {
      this.error.set('No se pudo dibujar el QR. Cancelá el cobro y probá de nuevo.');
    }
  }

  private startPolling(): void {
    if (this.pollTimer) return;
    this.pollTimer = setInterval(() => void this.pollCharge(), QR_POLL_MS);
  }

  private stopPolling(): void {
    if (this.pollTimer) clearInterval(this.pollTimer);
    this.pollTimer = null;
  }

  private async pollCharge(): Promise<void> {
    this.now.set(Date.now());
    const c = this.charge();
    if (!c || this.polling || this.cancelling()) return;
    this.polling = true;
    try {
      this.applyCharge(await this.mercadoPago.getCharge(c.id));
    } catch {
      // Un corte momentáneo no frena la espera: se reintenta en el próximo tick.
    } finally {
      this.polling = false;
    }
  }
}
