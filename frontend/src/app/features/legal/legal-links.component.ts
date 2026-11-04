import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Links a los papeles (paso 3.3): Términos, Privacidad y los botones de
 * arrepentimiento y de baja. Van en el login, el registro y las páginas
 * legales — visibles sin tener que entrar, como pide Defensa del Consumidor.
 */
@Component({
    selector: 'app-legal-links',
    imports: [RouterLink],
    template: `
    <nav class="legal-links" aria-label="Información legal">
      <a routerLink="/terminos">Términos y condiciones</a>
      <a routerLink="/privacidad">Política de privacidad</a>
      <a routerLink="/arrepentimiento" class="strong">Botón de arrepentimiento</a>
      <a routerLink="/baja" class="strong">Botón de baja</a>
    </nav>
  `,
    changeDetection: ChangeDetectionStrategy.Eager,
    styles: [
        `
      .legal-links {
        display: flex;
        flex-wrap: wrap;
        justify-content: center;
        gap: 0.35rem 1rem;
        font-size: 0.82rem;
      }
      .legal-links a {
        color: var(--text-muted);
        text-decoration: none;
      }
      .legal-links a:hover {
        color: var(--text);
        text-decoration: underline;
      }
      .legal-links a.strong {
        font-weight: 650;
      }
    `,
    ]
})
export class LegalLinksComponent {}
