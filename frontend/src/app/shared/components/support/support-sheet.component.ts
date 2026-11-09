import { Component, computed, effect, inject, input, output, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { DiagnosticsService } from '../../../core/services/diagnostics.service';
import { extractErrorMessage } from '../../../core/services/error.util';
import { SupportService } from '../../../core/services/support.service';
import { ToastService } from '../../../core/services/toast.service';
import { IconComponent } from '../icon/icon.component';
import { ModalComponent } from '../modal/modal.component';

/**
 * V18 — la ventana del botón "Soporte". Primero ofrece recargar (lo que
 * arregla la mayoría de las trabas); si no alcanza, abre WhatsApp con el
 * mensaje ya escrito y deja el pedido de ayuda en el panel de Fran.
 *
 * El botón de WhatsApp es un link común (no depende de que el servidor
 * conteste): si el servidor está caído, igual se abre WhatsApp.
 */
@Component({
    selector: 'app-support-sheet',
    imports: [FormsModule, IconComponent, ModalComponent],
    template: `
    <app-modal [open]="open()" title="¿Algo no anda?" subtitle="Te ayudamos. Probá primero recargar: arregla la mayoría de las trabas." size="narrow" (closed)="closed.emit()">
      <div class="stack">
        <button type="button" class="btn btn-secondary btn-block big" (click)="reload()">
          <app-icon name="refresh" /> Recargar la app
        </button>
        <p class="muted small hint">Lo que estabas cargando sin internet no se pierde.</p>

        <div class="divider"><span>¿Sigue trabado? Avisanos</span></div>

        <div class="field">
          <label for="support-what">¿Qué pasó? <span class="muted">(opcional)</span></label>
          <textarea
            id="support-what"
            rows="2"
            maxlength="500"
            data-no-autofocus
            placeholder="Ej.: no me deja cobrar la mesa 4"
            [ngModel]="what()"
            (ngModelChange)="what.set($event)"
          ></textarea>
        </div>

        @if (waUrl(); as url) {
          <a class="btn btn-block big wa" [href]="url" target="_blank" rel="noopener" (click)="notify(false)">
            <app-icon name="whatsapp" /> Escribir a soporte por WhatsApp
          </a>
          @if (loggedIn()) {
            <button type="button" class="btn btn-ghost btn-sm" [disabled]="sending()" (click)="notify(true)">
              Solo avisar, sin WhatsApp
            </button>
          }
        } @else if (loggedIn()) {
          <button type="button" class="btn btn-primary btn-block big" [disabled]="sending()" (click)="notify(true)">
            <app-icon name="send" /> Avisar a soporte
          </button>
        }
        @if (!waUrl() && support.info()?.contact) {
          <p class="muted small">{{ loggedIn() ? 'También podés comunicarte con' : 'Comunicate con' }} {{ support.info()?.contact }}.</p>
        }

        <details class="details">
          <summary>Qué datos se mandan</summary>
          <pre>{{ preview() }}</pre>
        </details>
      </div>
    </app-modal>
  `,
    changeDetection: ChangeDetectionStrategy.Eager,
    styles: [
        `
      .big {
        min-height: 52px;
        font-size: 1rem;
        gap: 0.5rem;
      }
      .hint {
        margin: -0.4rem 0 0;
        text-align: center;
      }
      .wa {
        background: #1f9d55;
        color: #fff;
        text-decoration: none;
      }
      .wa:hover {
        filter: brightness(1.06);
        color: #fff;
      }
      .divider {
        display: flex;
        align-items: center;
        gap: 0.6rem;
        color: var(--text-muted);
        font-size: 0.82rem;
        font-weight: 600;
      }
      .divider::before,
      .divider::after {
        content: '';
        flex: 1;
        height: 1px;
        background: var(--border);
      }
      textarea {
        resize: vertical;
      }
      .details summary {
        cursor: pointer;
        font-size: 0.82rem;
        color: var(--text-muted);
      }
      .details pre {
        white-space: pre-wrap;
        word-break: break-word;
        font-size: 0.78rem;
        background: var(--surface-2);
        border-radius: var(--radius-sm);
        padding: 0.6rem;
        margin: 0.4rem 0 0;
      }
    `,
    ]
})
export class SupportSheetComponent {
  readonly support = inject(SupportService);
  private readonly diagnostics = inject(DiagnosticsService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);
  readonly loggedIn = computed(() => !!this.auth.user());

  readonly open = input(false);
  readonly closed = output<void>();

  readonly what = signal('');
  readonly sending = signal(false);
  /** Cambia cada vez que se abre, así la hora y el último error del mensaje están al día. */
  private readonly openedAt = signal(0);

  readonly preview = computed(() => {
    this.openedAt();
    this.diagnostics.lastError();
    return this.support.buildMessage(this.page(), this.what());
  });
  readonly waUrl = computed(() => this.support.whatsappUrl(this.preview()));

  constructor() {
    effect(
      () => {
        if (this.open()) {
          this.openedAt.set(Date.now());
          void this.support.refresh().then(() => this.openedAt.set(Date.now()));
        }
      },
      { allowSignalWrites: true },
    );
  }

  reload(): void {
    window.location.reload();
  }

  /** Deja el pedido en el panel. Con WhatsApp no se espera: el link ya se está abriendo. */
  async notify(alone: boolean): Promise<void> {
    if (this.sending()) return;
    this.sending.set(true);
    try {
      if (!this.loggedIn()) return; // en el login no hay sesión: alcanza con el WhatsApp
      await this.support.send(this.page(), this.what());
      if (alone) this.toast.success('Listo, le avisamos a soporte. Te van a contactar.');
      this.what.set('');
      if (alone) this.closed.emit();
    } catch (error) {
      if (alone) this.toast.error(extractErrorMessage(error, 'No se pudo avisar. Probá por WhatsApp o recargá la app.'));
    } finally {
      this.sending.set(false);
    }
  }

  private page(): string {
    return this.router.url.split('?')[0] || '/';
  }
}
