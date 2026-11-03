import { LocationResponse, ReceiptPaperWidth } from '../models/business.model';
import { CashSessionResponse } from '../models/cash-session.model';
import { OrderPaymentResponse, OrderResponse, PAYMENT_METHOD_LABELS, PaymentMethod, itemOptionsLabel, latestBatch } from '../models/order.model';
import { TicketDocument, escapeHtml } from './ticket-print.service';

/**
 * Plantillas de los tres tickets térmicos de la Etapa 3 (ver README,
 * sección 14): comanda de cocina/barra, ticket para el cliente y cierre de
 * caja. Funciones puras (entran datos, sale un {@link TicketDocument}),
 * sin estado ni dependencias de Angular — así cualquier pantalla puede
 * imprimir el mismo ticket sin duplicar formato.
 *
 * <p>Plata y fechas en formato argentino (`es-AR`: "$ 1.234,50",
 * "22/09/2026 14:32") vía `Intl`, que ya viene en el navegador — no hace
 * falta registrar el locale de Angular solo para esto.
 */

/** Datos del encabezado que comparten los tickets. */
export interface TicketContext {
  businessName: string;
  location: LocationResponse | null;
}

const DEFAULT_PAPER_WIDTH: ReceiptPaperWidth = 80;

const moneyFormatter = new Intl.NumberFormat('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const dateTimeFormatter = new Intl.DateTimeFormat('es-AR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

/** "$ 1.234,50" / "-$ 800,00" — el signo va antes del "$", como se lee en un ticket argentino. */
function money(value: number | null | undefined): string {
  const amount = Number(value ?? 0);
  return `${amount < 0 ? '-' : ''}$ ${moneyFormatter.format(Math.abs(amount))}`;
}

function dateTime(iso: string | null | undefined): string {
  return iso ? dateTimeFormatter.format(new Date(iso)) : '—';
}

/** Número corto y legible para cantar/buscar un pedido — los primeros 8 caracteres del UUID. */
export function shortOrderNumber(order: OrderResponse): string {
  return order.id.slice(0, 8).toUpperCase();
}

function paperWidthOf(context: TicketContext): ReceiptPaperWidth {
  return context.location?.receiptPaperWidthMm ?? DEFAULT_PAPER_WIDTH;
}

function row(left: string, right: string, extraClass = ''): string {
  return `<div class="row ${extraClass}"><span>${left}</span><span>${right}</span></div>`;
}

const SEP = '<hr class="sep" />';

function businessHeader(context: TicketContext): string {
  const location = context.location;
  const lines = [`<p class="title">${escapeHtml(context.businessName) || 'COFFE_APP'}</p>`];
  if (location?.name) lines.push(`<p class="center">${escapeHtml(location.name)}</p>`);
  if (location?.address) lines.push(`<p class="center muted">${escapeHtml(location.address)}</p>`);
  if (location?.phone) lines.push(`<p class="center muted">Tel. ${escapeHtml(location.phone)}</p>`);
  return lines.join('\n');
}

/**
 * Comanda para cocina/barra — SIN precios: solo lo que hay que preparar,
 * con la mesa bien grande y las notas de cada ítem y del pedido a la vista.
 *
 * Si al pedido le agregaron productos después (tanda 2, 3…), la comanda
 * trae SOLO lo último agregado, con el título "AGREGADO": lo anterior ya
 * se había preparado.
 */
/** V19 — pedido de app: cliente y hora de retiro (vacío en un pedido común). */
function channelLines(order: OrderResponse): string {
  if (!order.channelName) return '';
  const lines: string[] = [];
  if (order.channelCustomer) lines.push(row('Cliente', escapeHtml(order.channelCustomer)));
  if (order.channelPickupAt) {
    const time = new Date(order.channelPickupAt).toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', hour12: false });
    lines.push(row('Retira', time, 'bold'));
  }
  return lines.join('\n');
}

export function buildKitchenTicket(order: OrderResponse, tableLabel: string, context: TicketContext): TicketDocument {
  const round = latestBatch(order);
  const toPrepare = round > 1 ? order.items.filter((item) => (item.batch ?? 1) === round) : order.items;
  const items = toPrepare
    .map((item) => {
      const note = item.notes ? `<p class="note">&gt; ${escapeHtml(item.notes)}</p>` : '';
      // V11 — cada opción en su renglón, para que se lea de un vistazo.
      const options = (item.options ?? []).map((option) => `<p class="opt">- ${escapeHtml(option.name)}</p>`).join('');
      return `<div class="item"><p class="item-name">${item.quantity} x ${escapeHtml(item.productName)}</p>${options}${note}</div>`;
    })
    .join('\n');

  const orderNote = order.notes && round === 1 ? `${SEP}<p class="bold">Nota del pedido:</p><p class="box">${escapeHtml(order.notes)}</p>` : '';

  const bodyHtml = `<div class="kitchen">
<p class="title">${round > 1 ? 'COMANDA · AGREGADO' : 'COMANDA'}</p>
<p class="huge">${escapeHtml(tableLabel)}</p>
${row(`Pedido #${shortOrderNumber(order)}`, dateTime(order.createdAt), 'muted')}
${channelLines(order)}
${SEP}
${items}
${orderNote}
${SEP}
<p class="center muted">Impresa ${dateTime(new Date().toISOString())}</p>
</div>`;

  return { title: `Comanda ${shortOrderNumber(order)}`, bodyHtml, paperWidthMm: paperWidthOf(context) };
}

/**
 * Ticket para el cliente — precuenta con precios, cobros y saldo. NO es
 * un comprobante fiscal (la factura electrónica AFIP/ARCA es la Etapa 4),
 * y lo dice explícitamente al pie.
 */
export function buildCustomerTicket(order: OrderResponse, tableLabel: string, context: TicketContext): TicketDocument {
  const items = order.items
    .map((item) => {
      const itemDiscount = (order.discounts ?? []).find((d) => d.orderItemId === item.id);
      const discount =
        item.discountAmount > 0
          ? row(`<span class="item-detail">${escapeHtml(itemDiscount?.name ?? 'Descuento')}</span>`, `-${money(item.discountAmount)}`)
          : '';
      const options = itemOptionsLabel(item);
      const optionsLine = options ? `<p class="item-detail">${escapeHtml(options)}</p>` : '';
      return `<div class="item">
<p class="item-name">${item.quantity} x ${escapeHtml(item.productName)}</p>
${optionsLine}
${row(`<span class="item-detail">${money(item.unitPrice)} c/u</span>`, money(item.unitPrice * item.quantity))}
${discount}
</div>`;
    })
    .join('\n');

  const orderDiscount = (order.discounts ?? []).find((d) => !d.orderItemId);
  const discountRow =
    order.discountTotal > 0 ? row(escapeHtml(orderDiscount?.name ?? 'Descuento'), `-${money(order.discountTotal)}`) : '';

  const payments =
    order.payments.length > 0
      ? `${SEP}<p class="bold">Pagos</p>\n${order.payments
          .map((payment) => row(escapeHtml(PAYMENT_METHOD_LABELS[payment.paymentMethod]), money(payment.amount)))
          .join('\n')}`
      : '';

  const balance =
    order.balanceDue > 0.001
      ? row('SALDO PENDIENTE', money(order.balanceDue), 'total')
      : '<p class="center bold">PAGADO</p>';

  const bodyHtml = `${businessHeader(context)}
${SEP}
${row('Fecha', dateTime(order.createdAt))}
${row('Pedido', `#${shortOrderNumber(order)}`)}
${row('Ubicación', escapeHtml(tableLabel))}
${channelLines(order)}
${SEP}
${items}
${SEP}
${row('Subtotal', money(order.subtotal))}
${discountRow}
${row('TOTAL', money(order.total), 'total')}
${payments}
${SEP}
${balance}
${SEP}
<p class="center muted">Comprobante no válido como factura</p>
<p class="center">¡Gracias por tu visita!</p>`;

  return { title: `Ticket ${shortOrderNumber(order)}`, bodyHtml, paperWidthMm: paperWidthOf(context) };
}

/**
 * Cierre de caja en formato ticket angosto — mismos números que el
 * reporte A4 de la pantalla de Caja (todos ya calculados por el backend,
 * ver `CashSessionService`), pero sin el detalle línea por línea de cobros,
 * que en un rollo de 58/80 mm se vuelve larguísimo en un turno normal.
 */
/**
 * V14 — ticket de UNA persona cuando se dividió la cuenta "por lo que
 * consumió cada uno": solo sus productos, lo que pagó y con qué. Igual que
 * el ticket del cliente, no es un comprobante fiscal.
 */
export function buildPartTicket(
  order: OrderResponse,
  payment: OrderPaymentResponse,
  tableLabel: string,
  context: TicketContext,
): TicketDocument {
  const lines = (payment.items ?? [])
    .map((line) => {
      const item = order.items.find((i) => i.id === line.orderItemId);
      if (!item) return '';
      const options = itemOptionsLabel(item);
      const optionsLine = options ? `<p class="item-detail">${escapeHtml(options)}</p>` : '';
      return `<div class="item">
<p class="item-name">${line.quantity} x ${escapeHtml(item.productName)}</p>
${optionsLine}
${row('<span class="item-detail">Tu parte</span>', money(line.amount))}
</div>`;
    })
    .join('\n');

  const bodyHtml = `${businessHeader(context)}
${SEP}
${row('Fecha', dateTime(payment.createdAt))}
${row('Pedido', `#${shortOrderNumber(order)}`)}
${row('Ubicación', escapeHtml(tableLabel))}
<p class="center bold">CUENTA DIVIDIDA · TU PARTE</p>
${SEP}
${lines}
${SEP}
${row('PAGASTE', money(payment.amount), 'total')}
${row('Con', escapeHtml(PAYMENT_METHOD_LABELS[payment.paymentMethod]))}
${SEP}
<p class="center muted">Comprobante no válido como factura</p>
<p class="center">¡Gracias por tu visita!</p>`;

  return { title: `Ticket ${shortOrderNumber(order)} (parte)`, bodyHtml, paperWidthMm: paperWidthOf(context) };
}

export function buildCashCloseTicket(session: CashSessionResponse, context: TicketContext): TicketDocument {
  const salesRows = (Object.entries(session.salesByMethod) as [PaymentMethod, number][])
    .map(([method, amount]) => row(escapeHtml(PAYMENT_METHOD_LABELS[method] ?? method), money(amount)))
    .join('\n');

  const movements =
    session.movements.length > 0
      ? `${SEP}<p class="bold">Movimientos de efectivo</p>\n${session.movements
          .map((movement) =>
            row(
              `${movement.type === 'INCOME' ? '+' : '-'} ${escapeHtml(movement.reason)}`,
              `${movement.type === 'INCOME' ? '' : '-'}${money(movement.amount)}`,
            ),
          )
          .join('\n')}`
      : '';

  const denominations =
    session.denominationBreakdown.length > 0
      ? `${SEP}<p class="bold">Conteo por billete</p>\n${session.denominationBreakdown
          .map((line) => row(`${line.quantity} x ${money(line.denomination)}`, money(line.subtotal)))
          .join('\n')}`
      : '';

  const difference =
    session.cashDifference !== null
      ? row(
          session.cashDifference < 0 ? 'FALTANTE' : session.cashDifference > 0 ? 'SOBRANTE' : 'DIFERENCIA',
          money(session.cashDifference),
          'total',
        )
      : '';

  const postnet = session.postnetBatchNumber
    ? `${SEP}${row('Lote Postnet', escapeHtml(session.postnetBatchNumber))}${row(
        'Monto Postnet',
        money(session.postnetReportedAmount),
      )}`
    : '';

  const notes = session.notes ? `${SEP}<p class="bold">Notas</p><p class="box">${escapeHtml(session.notes)}</p>` : '';

  const bodyHtml = `${businessHeader(context)}
${SEP}
<p class="title">CIERRE DE CAJA</p>
${row('Apertura', dateTime(session.openedAt))}
${row('Cierre', dateTime(session.closedAt))}
${row('Abrió', escapeHtml(session.openedByName ?? 'Desconocido'))}
${row('Cerró', escapeHtml(session.closedByName ?? 'Desconocido'))}
${SEP}
<p class="bold">Ventas por método</p>
${salesRows || '<p class="muted">Sin ventas en el turno.</p>'}
${row('TOTAL VENTAS', money(session.totalSales), 'total')}
${movements}
${SEP}
${row('Fondo inicial', money(session.openingAmount))}
${row('Ventas en efectivo', money(session.salesByMethod.CASH ?? 0))}
${row('Ingresos de efectivo', money(session.cashIncomeMovementsTotal))}
${row('Egresos de efectivo', `-${money(session.cashExpenseMovementsTotal)}`)}
${row('Efectivo esperado', money(session.expectedCashAmount), 'bold')}
${row('Efectivo contado', money(session.countedCashAmount), 'bold')}
${difference}
${denominations}
${postnet}
${notes}
${SEP}
<p class="center muted">Impreso ${dateTime(new Date().toISOString())}</p>`;

  return { title: 'Cierre de caja', bodyHtml, paperWidthMm: paperWidthOf(context) };
}
