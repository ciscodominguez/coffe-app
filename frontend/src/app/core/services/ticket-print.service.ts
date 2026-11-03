import { Injectable } from '@angular/core';
import { ReceiptPaperWidth } from '../models/business.model';

/** Escapa texto para meterlo en el HTML del ticket — TODO dato que venga de la base (nombres, notas) pasa por acá. */
export function escapeHtml(value: string | null | undefined): string {
  return (value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/** Un ticket listo para imprimir — lo arman las funciones de ticket-templates.ts. */
export interface TicketDocument {
  /** Título del documento (lo muestra el diálogo de impresión / el nombre sugerido al "Guardar como PDF"). */
  title: string;
  /** Cuerpo HTML del ticket. Todo dato dinámico ya viene escapado (ver {@link escapeHtml}). */
  bodyHtml: string;
  paperWidthMm: ReceiptPaperWidth;
}

/**
 * Ancho IMPRIMIBLE real de cada rollo: las ticketeras no imprimen de borde
 * a borde (un rollo de 80 mm imprime ~72 mm, uno de 58 mm ~48 mm). Usar el
 * ancho total del rollo corta el texto del lado derecho.
 */
const PRINTABLE_WIDTH_MM: Readonly<Record<ReceiptPaperWidth, number>> = {
  80: 72,
  58: 48,
};

const FONT_SIZE_PX: Readonly<Record<ReceiptPaperWidth, number>> = {
  80: 12,
  58: 10.5,
};

/** Red de seguridad por si el navegador nunca dispara `afterprint` (no debería pasar en Chrome/Edge). */
const CLEANUP_FALLBACK_MS = 60_000;

/**
 * Impresión térmica DESDE EL NAVEGADOR (Etapa 3, parte final — ver README,
 * sección 14). Fran eligió este camino en vez de un agente local ESC/POS:
 * cero programas extra para instalar en la PC del café, y funciona con
 * cualquier ticketera que tenga su driver instalado en Windows.
 *
 * <p>Cómo funciona: arma un documento HTML completo con el ancho del rollo
 * del local, lo carga en un iframe invisible y llama a `print()` SOBRE EL
 * IFRAME — nunca `window.print()` de la página, así lo que sale impreso es
 * solo el ticket y no depende del CSS `@media print` de cada pantalla (por
 * ejemplo, el reporte A4 de Caja sigue funcionando igual que antes).
 *
 * <p>No hay cola local de impresión a propósito: con impresión por
 * navegador el spooler de Windows ya encola los trabajos, y si la
 * ticketera está apagada el trabajo queda ahí hasta que se prenda. Una
 * cola propia solo tendría sentido con el agente ESC/POS, que quedó fuera
 * de alcance.
 */
@Injectable({ providedIn: 'root' })
export class TicketPrintService {
  print(ticket: TicketDocument): void {
    const iframe = document.createElement('iframe');
    iframe.setAttribute('aria-hidden', 'true');
    iframe.tabIndex = -1;
    Object.assign(iframe.style, {
      position: 'fixed',
      right: '0',
      bottom: '0',
      width: '0',
      height: '0',
      border: '0',
    });
    document.body.appendChild(iframe);

    const frameWindow = iframe.contentWindow;
    const frameDocument = iframe.contentDocument;
    if (!frameWindow || !frameDocument) {
      iframe.remove();
      throw new Error('El navegador no permitió preparar la impresión.');
    }

    let cleanedUp = false;
    const cleanup = () => {
      if (cleanedUp) return;
      cleanedUp = true;
      iframe.remove();
    };

    frameDocument.open();
    frameDocument.write(this.buildDocument(ticket));
    frameDocument.close();

    frameWindow.addEventListener('afterprint', () => setTimeout(cleanup, 0));
    setTimeout(cleanup, CLEANUP_FALLBACK_MS);

    // Un tick para que el iframe termine de maquetar antes de imprimir.
    setTimeout(() => {
      frameWindow.focus();
      frameWindow.print();
    }, 50);
  }

  private buildDocument(ticket: TicketDocument): string {
    const contentWidth = PRINTABLE_WIDTH_MM[ticket.paperWidthMm];
    const fontSize = FONT_SIZE_PX[ticket.paperWidthMm];
    return `<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8" />
<title>${escapeHtml(ticket.title)}</title>
<style>
  @page { margin: 0; }
  * { box-sizing: border-box; }
  html, body { margin: 0; padding: 0; background: #fff; color: #000; }
  body {
    width: ${contentWidth}mm;
    margin: 0 auto;
    padding: 2mm 0 6mm;
    font-family: 'Courier New', Courier, monospace;
    font-size: ${fontSize}px;
    line-height: 1.3;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }
  p { margin: 0; }
  .center { text-align: center; }
  .bold { font-weight: 700; }
  .title { font-size: 1.35em; font-weight: 700; text-align: center; letter-spacing: 0.05em; }
  .huge { font-size: 1.9em; font-weight: 700; text-align: center; margin: 1mm 0; }
  .muted { font-size: 0.9em; }
  .sep { border: 0; border-top: 1px dashed #000; margin: 2mm 0; }
  .row { display: flex; justify-content: space-between; gap: 2mm; }
  .row > span:first-child { flex: 1; overflow-wrap: break-word; }
  .row > span:last-child { white-space: nowrap; text-align: right; }
  .total { font-size: 1.2em; font-weight: 700; }
  .item { margin-bottom: 1mm; }
  .item-name { font-weight: 700; overflow-wrap: anywhere; }
  .kitchen .item-name { font-size: 1.25em; }
  .item-detail { padding-left: 4mm; font-size: 0.9em; }
  .opt { padding-left: 4mm; font-weight: 700; overflow-wrap: anywhere; }
  .kitchen .opt { font-size: 1.1em; }
  .note { padding-left: 4mm; font-style: italic; overflow-wrap: anywhere; }
  .box { border: 1px solid #000; padding: 1mm 2mm; margin-top: 1mm; overflow-wrap: anywhere; }
</style>
</head>
<body>
${ticket.bodyHtml}
</body>
</html>`;
  }
}
