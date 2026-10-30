import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { ToastService } from '../../../core/services/toast.service';
import { IconComponent } from '../icon/icon.component';

/** Muestra los avisos de ToastService. Va una sola vez, en AppComponent. */
@Component({
    selector: 'app-toast-host',
    imports: [IconComponent],
    template: `
    <div class="toasts" role="status" aria-live="polite">
      @for (toast of toastService.toasts(); track toast.id) {
        <div class="toast" [class]="'toast toast-' + toast.kind">
          <app-icon [name]="toast.kind === 'success' ? 'check' : toast.kind === 'error' ? 'alert' : 'info'" />
          <span class="msg">{{ toast.message }}</span>
          <button type="button" class="close" (click)="toastService.dismiss(toast.id)" aria-label="Cerrar aviso">
            <app-icon name="x" [size]="16" />
          </button>
        </div>
      }
    </div>
  `,
    changeDetection: ChangeDetectionStrategy.Eager,
    styles: [
        `
      .toasts {
        position: fixed;
        left: 50%;
        transform: translateX(-50%);
        bottom: calc(1.25rem + env(safe-area-inset-bottom));
        z-index: 2000;
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
        width: min(440px, calc(100vw - 1.5rem));
        pointer-events: none;
      }
      @media (max-width: 767px) {
        .toasts {
          bottom: calc(80px + env(safe-area-inset-bottom));
        }
      }
      .toast {
        pointer-events: auto;
        display: flex;
        align-items: center;
        gap: 0.65rem;
        padding: 0.8rem 0.9rem;
        border-radius: var(--radius);
        background: var(--text);
        color: var(--bg);
        box-shadow: var(--shadow-lg);
        font-weight: 550;
        font-size: 0.93rem;
        animation: in 180ms ease;
      }
      .toast-success app-icon {
        color: var(--success);
      }
      .toast-error {
        background: var(--danger);
        color: #fff;
      }
      .msg {
        flex: 1;
      }
      .close {
        all: unset;
        cursor: pointer;
        opacity: 0.7;
        display: inline-flex;
        padding: 4px;
      }
      @keyframes in {
        from {
          opacity: 0;
          transform: translateY(8px);
        }
      }
    `,
    ]
})
export class ToastHostComponent {
  readonly toastService = inject(ToastService);
}
