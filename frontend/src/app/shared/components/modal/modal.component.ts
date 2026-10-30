import { Component, ElementRef, HostListener, effect, inject, input, output, ChangeDetectionStrategy } from '@angular/core';
import { IconComponent } from '../icon/icon.component';

/**
 * Ventana emergente reutilizable. En la compu/tablet aparece centrada; en
 * el celular sube desde abajo ("hoja"), que es más cómodo con el pulgar.
 *
 * Uso:
 *   <app-modal [open]="x()" title="Nuevo producto" (closed)="x.set(false)">
 *     …contenido…
 *     <div modal-footer>…botones…</div>
 *   </app-modal>
 *
 * Se cierra con Escape, con la X o tocando afuera (salvo `[dismissable]="false"`,
 * para pasos donde cerrar por accidente haría perder lo cargado).
 */
@Component({
    selector: 'app-modal',
    imports: [IconComponent],
    template: `
    @if (open()) {
      <div class="backdrop" (click)="onBackdrop()"></div>
      <div
        class="dialog"
        [class.wide]="size() === 'wide'"
        [class.narrow]="size() === 'narrow'"
        role="dialog"
        aria-modal="true"
        [attr.aria-label]="title()"
      >
        <header class="dialog-header">
          <div class="dialog-titles">
            <h2>{{ title() }}</h2>
            @if (subtitle()) {
              <p class="dialog-subtitle">{{ subtitle() }}</p>
            }
          </div>
          @if (closable()) {
            <button type="button" class="btn btn-ghost btn-icon" (click)="closed.emit()" aria-label="Cerrar">
              <app-icon name="x" />
            </button>
          }
        </header>
        <div class="dialog-body">
          <ng-content />
        </div>
        <footer class="dialog-footer">
          <ng-content select="[modal-footer]" />
        </footer>
      </div>
    }
  `,
    changeDetection: ChangeDetectionStrategy.Eager,
    styles: [
        `
      .backdrop {
        position: fixed;
        inset: 0;
        background: var(--overlay);
        z-index: 1000;
        animation: fade 150ms ease;
      }
      .dialog {
        position: fixed;
        z-index: 1001;
        left: 50%;
        top: 50%;
        transform: translate(-50%, -50%);
        width: min(560px, calc(100vw - 2rem));
        max-height: calc(100dvh - 2rem);
        display: flex;
        flex-direction: column;
        background: var(--surface);
        color: var(--text);
        border-radius: var(--radius-lg);
        box-shadow: var(--shadow-lg);
        animation: pop 170ms ease;
      }
      .dialog.wide {
        width: min(820px, calc(100vw - 2rem));
      }
      .dialog.narrow {
        width: min(420px, calc(100vw - 2rem));
      }
      .dialog-header {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 1rem;
        padding: 1.1rem 1.25rem 0.5rem;
      }
      .dialog-titles {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
        padding-top: 0.4rem;
      }
      .dialog-subtitle {
        color: var(--text-muted);
        font-size: 0.9rem;
      }
      .dialog-body {
        padding: 0.5rem 1.25rem 1rem;
        overflow-y: auto;
        flex: 1;
      }
      .dialog-footer {
        display: flex;
        gap: 0.5rem;
        justify-content: flex-end;
        flex-wrap: wrap;
        padding: 0 1.25rem 1.1rem;
      }
      .dialog-footer:empty {
        display: none;
      }
      @media (max-width: 640px) {
        .dialog,
        .dialog.wide,
        .dialog.narrow {
          left: 0;
          right: 0;
          top: auto;
          bottom: 0;
          transform: none;
          width: 100%;
          max-height: 92dvh;
          border-radius: var(--radius-lg) var(--radius-lg) 0 0;
          padding-bottom: env(safe-area-inset-bottom);
          animation: slide-up 200ms ease;
        }
        .dialog-footer {
          position: sticky;
          bottom: 0;
          background: var(--surface);
          padding-top: 0.75rem;
          border-top: 1px solid var(--border);
        }
        .dialog-footer ::ng-deep > * {
          flex: 1;
        }
      }
      @keyframes fade {
        from {
          opacity: 0;
        }
      }
      @keyframes pop {
        from {
          opacity: 0;
          transform: translate(-50%, -48%) scale(0.98);
        }
      }
      @keyframes slide-up {
        from {
          transform: translateY(40px);
          opacity: 0;
        }
      }
    `,
    ]
})
export class ModalComponent {
  readonly open = input<boolean>(false);
  readonly title = input<string>('');
  readonly subtitle = input<string>('');
  readonly size = input<'normal' | 'wide' | 'narrow'>('normal');
  readonly dismissable = input<boolean>(true);
  /** Sin la X de cerrar (V17: aceptar términos nuevos, que no se puede saltear). */
  readonly closable = input<boolean>(true);
  readonly closed = output<void>();

  private readonly host = inject(ElementRef<HTMLElement>);

  constructor() {
    // Al abrir: bloquea el scroll de fondo y enfoca el primer campo, así se
    // puede escribir sin tocar nada más.
    effect(() => {
      const isOpen = this.open();
      document.body.style.overflow = isOpen ? 'hidden' : '';
      if (isOpen) {
        setTimeout(() => {
          const first = this.host.nativeElement.querySelector(
            '.dialog-body input:not([type=hidden]):not([disabled]):not([data-no-autofocus]), .dialog-body select:not([data-no-autofocus]), .dialog-body textarea:not([data-no-autofocus])',
          ) as HTMLElement | null;
          if (first && window.matchMedia('(pointer: fine)').matches) first.focus();
        }, 60);
      }
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.open() && this.dismissable()) this.closed.emit();
  }

  onBackdrop(): void {
    if (this.dismissable()) this.closed.emit();
  }
}
