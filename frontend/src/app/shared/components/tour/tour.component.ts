import { Component, HostListener, effect, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { TourService } from '../../../core/services/tour.service';
import { IconComponent } from '../icon/icon.component';

interface Rect {
  top: number;
  left: number;
  width: number;
  height: number;
}

const PAD = 6;
const POPOVER_WIDTH = 320;

/**
 * Dibuja el recorrido de TourService: oscurece la pantalla, "recorta" el
 * elemento señalado y muestra al lado una tarjetita con el texto y los
 * botones Anterior / Siguiente. Si el elemento no está a la vista (por
 * ejemplo, un ítem del menú que en el celular vive dentro de "Más"), la
 * tarjeta aparece centrada igual.
 */
@Component({
    selector: 'app-tour',
    imports: [IconComponent],
    template: `
    @if (tour.current(); as step) {
      <div class="tour-layer" (click)="$event.stopPropagation()">
        @if (rect(); as r) {
          <div
            class="spotlight"
            [style.top.px]="r.top - pad"
            [style.left.px]="r.left - pad"
            [style.width.px]="r.width + pad * 2"
            [style.height.px]="r.height + pad * 2"
          ></div>
        } @else {
          <div class="dim"></div>
        }
        <div
          class="popover"
          [class.centered]="!rect()"
          [style.top.px]="popTop()"
          [style.left.px]="popLeft()"
          role="dialog"
          aria-live="polite"
          [attr.aria-label]="step.title"
        >
          <div class="pop-head">
            <span class="counter">{{ tour.index() + 1 }} de {{ tour.steps().length }}</span>
            <button type="button" class="skip" (click)="tour.finish()">Saltear</button>
          </div>
          <h3>{{ step.title }}</h3>
          <p>{{ step.text }}</p>
          <div class="dots">
            @for (s of tour.steps(); track $index) {
              <span class="dot" [class.on]="$index === tour.index()"></span>
            }
          </div>
          <div class="pop-actions">
            @if (tour.index() > 0) {
              <button type="button" class="btn btn-ghost" (click)="tour.prev()">
                <app-icon name="arrow-left" [size]="18" /> Anterior
              </button>
            }
            <span class="spacer"></span>
            <button type="button" class="btn btn-primary" (click)="tour.next()">
              @if (tour.isLast()) {
                <app-icon name="check" [size]="18" /> ¡Listo!
              } @else {
                Siguiente <app-icon name="arrow-right" [size]="18" />
              }
            </button>
          </div>
        </div>
      </div>
    }
  `,
    changeDetection: ChangeDetectionStrategy.Eager,
    styles: [
        `
      .tour-layer {
        position: fixed;
        inset: 0;
        z-index: 3000;
      }
      .dim {
        position: fixed;
        inset: 0;
        background: var(--overlay);
      }
      .spotlight {
        position: fixed;
        border-radius: var(--radius);
        box-shadow:
          0 0 0 9999px var(--overlay),
          0 0 0 3px var(--primary);
        transition: all 220ms ease;
        pointer-events: none;
      }
      .popover {
        position: fixed;
        width: min(320px, calc(100vw - 24px));
        background: var(--surface);
        color: var(--text);
        border-radius: var(--radius-lg);
        box-shadow: var(--shadow-lg);
        padding: 1rem 1.1rem;
        display: flex;
        flex-direction: column;
        gap: 0.55rem;
        transition:
          top 220ms ease,
          left 220ms ease;
        animation: pop 180ms ease;
      }
      .popover.centered {
        top: 50% !important;
        left: 50% !important;
        transform: translate(-50%, -50%);
      }
      .pop-head {
        display: flex;
        justify-content: space-between;
        align-items: center;
      }
      .counter {
        font-size: 0.75rem;
        font-weight: 700;
        color: var(--primary);
        text-transform: uppercase;
        letter-spacing: 0.05em;
      }
      .skip {
        all: unset;
        cursor: pointer;
        font-size: 0.85rem;
        color: var(--text-muted);
        padding: 4px;
      }
      p {
        color: var(--text-muted);
        font-size: 0.93rem;
      }
      .dots {
        display: flex;
        gap: 5px;
        margin-top: 0.2rem;
      }
      .dot {
        width: 7px;
        height: 7px;
        border-radius: 50%;
        background: var(--border-strong);
      }
      .dot.on {
        background: var(--primary);
        width: 18px;
        border-radius: 4px;
      }
      .pop-actions {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        margin-top: 0.3rem;
      }
      @keyframes pop {
        from {
          opacity: 0;
        }
      }
    `,
    ]
})
export class TourComponent {
  readonly tour = inject(TourService);
  readonly pad = PAD;

  readonly rect = signal<Rect | null>(null);
  readonly popTop = signal(0);
  readonly popLeft = signal(0);

  constructor() {
    effect(() => {
      const step = this.tour.current();
      // Espera un frame: el paso anterior pudo haber abierto/cerrado algo.
      setTimeout(() => this.position(step?.target), 30);
    });
  }

  @HostListener('window:resize')
  onResize(): void {
    this.position(this.tour.current()?.target);
  }

  @HostListener('document:keydown', ['$event'])
  onKey(event: KeyboardEvent): void {
    if (!this.tour.active()) return;
    if (event.key === 'Escape') this.tour.finish();
    if (event.key === 'ArrowRight') this.tour.next();
    if (event.key === 'ArrowLeft') this.tour.prev();
  }

  private position(target: string | undefined): void {
    const element = target ? this.findVisible(target) : null;
    if (!element) {
      this.rect.set(null);
      return;
    }
    element.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    const box = element.getBoundingClientRect();
    this.rect.set({ top: box.top, left: box.left, width: box.width, height: box.height });

    const viewportW = window.innerWidth;
    const viewportH = window.innerHeight;
    const popW = Math.min(POPOVER_WIDTH, viewportW - 24);
    // Alto real de la tarjeta si ya está dibujada (el texto cambia de largo en cada paso).
    const popEl = document.querySelector('app-tour .popover') as HTMLElement | null;
    const estimatedPopH = Math.max(popEl?.offsetHeight ?? 0, 240);

    // Menú lateral (a la izquierda): la tarjeta va a la derecha del ítem.
    if (box.right + popW + 24 < viewportW && box.left < viewportW / 3 && box.height < 80) {
      this.popLeft.set(box.right + 16);
      this.popTop.set(Math.min(Math.max(12, box.top - 20), viewportH - estimatedPopH - 12));
      return;
    }
    // Si no, abajo o arriba del elemento, lo que tenga más lugar.
    const spaceBelow = viewportH - box.bottom;
    const top = spaceBelow > estimatedPopH + 20 ? box.bottom + 14 : Math.max(12, box.top - estimatedPopH - 14);
    const left = Math.min(Math.max(12, box.left + box.width / 2 - popW / 2), viewportW - popW - 12);
    this.popTop.set(top);
    this.popLeft.set(left);
  }

  private findVisible(target: string): HTMLElement | null {
    const candidates = Array.from(document.querySelectorAll<HTMLElement>(`[data-tour="${target}"]`));
    return (
      candidates.find((el) => {
        const r = el.getBoundingClientRect();
        return r.width > 0 && r.height > 0 && getComputedStyle(el).visibility !== 'hidden';
      }) ?? null
    );
  }
}
