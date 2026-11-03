import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Component, OnInit, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import {
  CASH_DENOMINATIONS,
  CASH_MOVEMENT_TYPE_LABELS,
  CashMovementType,
  CashSessionResponse,
  DenominationCountLineInput,
} from '../../core/models/cash-session.model';
import { PAYMENT_METHOD_LABELS, PaymentMethod } from '../../core/models/order.model';
import { LocationResponse } from '../../core/models/business.model';
import { AuthService } from '../../core/services/auth.service';
import { BusinessService, LocationService } from '../../core/services/business.service';
import { CashSessionService } from '../../core/services/cash-session.service';
import { extractErrorMessage } from '../../core/services/error.util';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { ModalComponent } from '../../shared/components/modal/modal.component';
import { TicketPrintService } from '../../core/services/ticket-print.service';
import { buildCashCloseTicket } from '../../core/services/ticket-templates';

const HISTORY_LIMIT = 10;
const MOVEMENT_TYPES: readonly CashMovementType[] = ['INCOME', 'EXPENSE'];

/**
 * Pantalla de caja — ítem 14 de las mejoras pedidas, más las mejoras "caja
 * más profesional" pedidas por Fran después (ver README, sección 10):
 *
 * <ol>
 *   <li>Nombres reales, no solo totales — quién abrió/cerró el turno,
 *       quién cargó cada movimiento/cobro, y el detalle línea por línea de
 *       qué se cobró (no solo el agregado por método).
 *   <li>Conteo de efectivo por billete al cerrar, en vez de un único monto
 *       suelto — ver {@link CASH_DENOMINATIONS}.
 *   <li>Reporte de cierre imprimible/exportable — {@link viewReport} +
 *       {@link printReport}, con CSS de impresión en el .css de este
 *       componente (sin dependencias nuevas: usa {@code window.print()}).
 * </ol>
 *
 * Tres estados posibles para el turno del local: (1) no hay turno abierto
 * → formulario para abrir uno; (2) hay un turno abierto → resumen en vivo
 * + carga de movimientos + botón para cerrar; (3) formulario de cierre
 * abierto → conteo por billete y, si corresponde, el lote de Postnet
 * (ítem 15). El historial de abajo muestra los últimos turnos ya cerrados
 * de este local, cada uno con su reporte imprimible.
 */
@Component({
    selector: 'app-cash-session',
    imports: [CommonModule, FormsModule, IconComponent, ModalComponent],
    templateUrl: './cash-session.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './cash-session.component.css'
})
export class CashSessionComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly locationService = inject(LocationService);
  private readonly businessService = inject(BusinessService);
  private readonly cashSessionService = inject(CashSessionService);
  private readonly ticketPrint = inject(TicketPrintService);
  private readonly toast = inject(ToastService);

  private resolvedLocationId: string | null = null;

  readonly movementTypes = MOVEMENT_TYPES;
  readonly movementTypeLabels = CASH_MOVEMENT_TYPE_LABELS;
  readonly paymentMethodLabels = PAYMENT_METHOD_LABELS;
  readonly denominations = CASH_DENOMINATIONS;

  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  readonly currentSession = signal<CashSessionResponse | null>(null);
  readonly history = signal<CashSessionResponse[]>([]);

  /** Nombre del negocio/local — solo para encabezar el reporte imprimible, ver {@link viewReport}. */
  readonly businessName = signal('');
  readonly locationName = signal('');
  /** Local completo (con su ancho de ticketera) — para {@link printReportTicket}. */
  private reportLocation: LocationResponse | null = null;

  readonly salesByMethodEntries = computed(() => {
    const session = this.currentSession();
    if (!session) return [] as [PaymentMethod, number][];
    return Object.entries(session.salesByMethod) as [PaymentMethod, number][];
  });

  // ---- Abrir turno ----
  readonly openingAmount = signal<number>(0);
  readonly openNotes = signal('');
  readonly opening = signal(false);

  // ---- Movimiento manual ----
  readonly movementType = signal<CashMovementType>('EXPENSE');
  readonly movementAmount = signal<number>(0);
  readonly movementReason = signal('');
  readonly addingMovement = signal(false);
  readonly movementError = signal<string | null>(null);

  readonly showMovementModal = signal(false);
  /** Motivos frecuentes: un toque y listo (se pueden editar). */
  readonly quickReasons: Readonly<Record<CashMovementType, readonly string[]>> = {
    EXPENSE: ['Pago a proveedor', 'Retiro del dueño', 'Compra de insumos', 'Propinas'],
    INCOME: ['Cambio / sencillo', 'Aporte del dueño', 'Corrección'],
  };
  /** Sugerencias de fondo inicial para abrir la caja con un toque. */
  readonly openingSuggestions: readonly number[] = [0, 10000, 20000, 50000];
  /** Pestaña del detalle del turno abierto. */
  readonly detailTab = signal<'payments' | 'movements'>('payments');

  // ---- Cerrar turno (en 3 pasos) ----
  readonly showCloseForm = signal(false);
  readonly closeStep = signal<1 | 2 | 3>(1);
  /** Conteo de efectivo por billete — mejora "caja más profesional". Clave: denominación, valor: cantidad de billetes. */
  readonly denominationCounts = signal<Record<number, number>>(this.emptyDenominationCounts());
  readonly postnetBatchNumber = signal('');
  readonly postnetReportedAmount = signal<number | null>(null);
  readonly closeNotes = signal('');
  readonly closing = signal(false);
  readonly closeError = signal<string | null>(null);

  /** Total contado — suma del conteo por billete, lo que el backend va a guardar como `countedCashAmount`. */
  readonly countedCashAmount = computed(() => {
    const counts = this.denominationCounts();
    return this.denominations.reduce((sum, denom) => sum + denom * (counts[denom] ?? 0), 0);
  });

  readonly closeDifferencePreview = computed(() => {
    const session = this.currentSession();
    if (!session) return null;
    return this.countedCashAmount() - session.expectedCashAmount;
  });

  // ---- Reporte de cierre (imprimible) ----
  readonly selectedReport = signal<CashSessionResponse | null>(null);

  async ngOnInit(): Promise<void> {
    try {
      const locationId = await this.resolveLocationId();
      this.resolvedLocationId = locationId;
      await Promise.all([this.loadCurrent(locationId), this.loadHistory(locationId), this.loadReportHeader(locationId)]);
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudo cargar la pantalla de caja.'));
    } finally {
      this.loading.set(false);
    }
  }

  async openSession(): Promise<void> {
    const locationId = this.resolvedLocationId;
    if (this.opening() || !locationId) return;
    if (this.openingAmount() < 0) {
      this.errorMessage.set('El fondo inicial no puede ser negativo.');
      return;
    }
    this.opening.set(true);
    this.errorMessage.set(null);
    try {
      const session = await this.cashSessionService.open({
        locationId,
        openingAmount: this.openingAmount(),
        notes: this.openNotes().trim() || null,
      });
      this.currentSession.set(session);
      this.openingAmount.set(0);
      this.openNotes.set('');
      this.toast.success('Caja abierta. ¡Buen turno!');
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudo abrir el turno de caja.'));
    } finally {
      this.opening.set(false);
    }
  }

  async addMovement(): Promise<void> {
    const session = this.currentSession();
    if (this.addingMovement() || !session) return;
    if (this.movementAmount() <= 0) {
      this.movementError.set('Ingresá un monto mayor a 0.');
      return;
    }
    if (!this.movementReason().trim()) {
      this.movementError.set('Contá el motivo del movimiento.');
      return;
    }
    this.addingMovement.set(true);
    this.movementError.set(null);
    try {
      await this.cashSessionService.addMovement(session.id, {
        type: this.movementType(),
        amount: this.movementAmount(),
        reason: this.movementReason().trim(),
      });
      await this.refreshCurrent(session.id);
      this.movementAmount.set(0);
      this.movementReason.set('');
      this.showMovementModal.set(false);
      this.detailTab.set('movements');
      this.toast.success('Movimiento registrado.');
    } catch (error) {
      this.movementError.set(extractErrorMessage(error, 'No se pudo cargar el movimiento.'));
    } finally {
      this.addingMovement.set(false);
    }
  }

  /**
   * Etiqueta de un método de pago a partir de la clave `string` que devuelve
   * el pipe `keyvalue` sobre `salesByMethod` (ver el reporte imprimible) —
   * un método propio en vez de indexar `paymentMethodLabels` directo en la
   * plantilla, porque TS no deja indexar un `Record<PaymentMethod, string>`
   * con una clave tipada como `string` sin este paso intermedio.
   */
  paymentLabel(method: string): string {
    return this.paymentMethodLabels[method as PaymentMethod] ?? method;
  }

  /** Cantidad de billetes de una denominación puntual — usado por el input del conteo (ver la plantilla). */
  denominationQuantity(denomination: number): number {
    return this.denominationCounts()[denomination] ?? 0;
  }

  setDenominationQuantity(denomination: number, quantity: number): void {
    const safeQuantity = Number.isFinite(quantity) && quantity >= 0 ? Math.trunc(quantity) : 0;
    this.denominationCounts.set({ ...this.denominationCounts(), [denomination]: safeQuantity });
  }

  toggleCloseForm(): void {
    const session = this.currentSession();
    this.showCloseForm.set(!this.showCloseForm());
    this.closeStep.set(1);
    this.denominationCounts.set(this.emptyDenominationCounts());
    this.postnetBatchNumber.set('');
    this.postnetReportedAmount.set(null);
    this.closeNotes.set('');
    this.closeError.set(null);
    void session; // El fondo esperado se sigue mostrando en vivo desde currentSession(); el conteo por billete arranca en cero a propósito (contar de nuevo, no prellenar con lo "esperado").
  }

  async closeSession(): Promise<void> {
    const session = this.currentSession();
    if (this.closing() || !session) return;

    const breakdown: DenominationCountLineInput[] = this.denominations
      .map((denomination) => ({ denomination, quantity: this.denominationQuantity(denomination) }))
      .filter((line) => line.quantity > 0);

    this.closing.set(true);
    this.closeError.set(null);
    try {
      const closed = await this.cashSessionService.close(session.id, {
        countedCashAmount: this.countedCashAmount(),
        denominationBreakdown: breakdown,
        postnetBatchNumber: this.postnetBatchNumber().trim() || null,
        postnetReportedAmount: this.postnetReportedAmount(),
        notes: this.closeNotes().trim() || null,
      });
      this.currentSession.set(null);
      this.showCloseForm.set(false);
      this.history.set([closed, ...this.history()].slice(0, HISTORY_LIMIT));
      // Muestra el reporte del turno recién cerrado de una: es el momento en que más se necesita imprimirlo/exportarlo.
      this.selectedReport.set(closed);
      this.toast.success('Caja cerrada.');
    } catch (error) {
      this.closeError.set(extractErrorMessage(error, 'No se pudo cerrar el turno.'));
    } finally {
      this.closing.set(false);
    }
  }

  openMovementModal(type: CashMovementType): void {
    this.movementType.set(type);
    this.movementAmount.set(0);
    this.movementReason.set('');
    this.movementError.set(null);
    this.showMovementModal.set(true);
  }

  /** Botones +/− del conteo por billete (más rápido que tipear en el celular). */
  bumpDenomination(denomination: number, delta: number): void {
    this.setDenominationQuantity(denomination, Math.max(0, this.denominationQuantity(denomination) + delta));
  }

  /** Porcentaje de cada método sobre el total vendido — para las barras del resumen. */
  salesShare(amount: number): number {
    const total = this.currentSession()?.totalSales ?? 0;
    return total > 0 ? Math.round((amount / total) * 100) : 0;
  }

  /** Ítem "reporte de cierre para imprimir/exportar" — abre el detalle imprimible de un turno cerrado. */
  viewReport(session: CashSessionResponse): void {
    this.selectedReport.set(session);
  }

  /**
   * Etapa 3 — el mismo cierre, en formato ticket angosto para la ticketera
   * del local (58/80 mm). Convive con {@link printReport} (A4/PDF): no lo
   * reemplaza.
   */
  printReportTicket(session: CashSessionResponse): void {
    try {
      this.ticketPrint.print(
        buildCashCloseTicket(session, { businessName: this.businessName(), location: this.reportLocation }),
      );
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudo imprimir el ticket de cierre.'));
    }
  }

  closeReport(): void {
    this.selectedReport.set(null);
  }

  /** {@code window.print()} nativo del navegador — el CSS de este componente oculta todo menos el reporte al imprimir (o exportar como PDF desde el diálogo de impresión). */
  printReport(): void {
    window.print();
  }

  private emptyDenominationCounts(): Record<number, number> {
    return Object.fromEntries(this.denominations.map((denomination) => [denomination, 0]));
  }

  private async loadCurrent(locationId: string): Promise<void> {
    try {
      this.currentSession.set(await this.cashSessionService.findCurrent(locationId));
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 404) {
        this.currentSession.set(null);
        return;
      }
      throw error;
    }
  }

  private async refreshCurrent(id: string): Promise<void> {
    this.currentSession.set(await this.cashSessionService.findOne(id));
  }

  private async loadHistory(locationId: string): Promise<void> {
    const sessions = await this.cashSessionService.findAllByLocation(locationId);
    this.history.set(
      sessions
        .filter((session) => session.status === 'CLOSED')
        .sort((a, b) => (b.closedAt ?? '').localeCompare(a.closedAt ?? ''))
        .slice(0, HISTORY_LIMIT),
    );
  }

  /** Solo para el encabezado del reporte imprimible — nombre del negocio y del local. */
  private async loadReportHeader(locationId: string): Promise<void> {
    // getDisplayName (no getMe): GET /businesses/me es solo de gestión en
    // el backend, y la Caja también la usan los WAITER.
    const [business, locations] = await Promise.all([
      this.businessService.getDisplayName(),
      this.locationService.findAll(),
    ]);
    this.businessName.set(business.displayName);
    const location = locations.find((l: LocationResponse) => l.id === locationId);
    this.locationName.set(location?.name ?? '');
    this.reportLocation = location ?? null;
  }

  /** Mismo criterio que WaiterOrderComponent/KdsBoardComponent — ver el Javadoc de cualquiera de los dos. */
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
