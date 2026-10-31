import { Component, input, ChangeDetectionStrategy } from '@angular/core';
import { IconComponent, IconName } from '../icon/icon.component';

/**
 * Pantalla vacía que explica: qué va a aparecer acá, por qué todavía no hay
 * nada y (con contenido proyectado) el botón para hacerlo.
 */
@Component({
    selector: 'app-empty-state',
    imports: [IconComponent],
    template: `
    <div class="empty" [class.compact]="compact()">
      <div class="icon-wrap"><app-icon [name]="icon()" [size]="compact() ? 24 : 30" /></div>
      <h3>{{ title() }}</h3>
      @if (text()) {
        <p>{{ text() }}</p>
      }
      <div class="actions"><ng-content /></div>
    </div>
  `,
    changeDetection: ChangeDetectionStrategy.Eager,
    styles: [
        `
      .empty {
        display: flex;
        flex-direction: column;
        align-items: center;
        text-align: center;
        gap: 0.6rem;
        padding: 2.5rem 1.25rem;
        border: 1.5px dashed var(--border-strong);
        border-radius: var(--radius-lg);
        background: color-mix(in srgb, var(--surface) 60%, transparent);
      }
      .empty.compact {
        padding: 1.5rem 1rem;
      }
      .icon-wrap {
        width: 60px;
        height: 60px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        background: var(--primary-soft);
        color: var(--primary);
        margin-bottom: 0.25rem;
      }
      .compact .icon-wrap {
        width: 46px;
        height: 46px;
      }
      p {
        color: var(--text-muted);
        max-width: 46ch;
        font-size: 0.93rem;
      }
      .actions {
        display: flex;
        gap: 0.5rem;
        flex-wrap: wrap;
        justify-content: center;
        margin-top: 0.4rem;
      }
      .actions:empty {
        display: none;
      }
    `,
    ]
})
export class EmptyStateComponent {
  readonly icon = input<IconName>('info');
  readonly title = input.required<string>();
  readonly text = input<string>('');
  readonly compact = input<boolean>(false);
}
