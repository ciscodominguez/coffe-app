import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { LEGAL_INFO, isTodo } from './legal-info';
import { LegalLinksComponent } from './legal-links.component';

/** Términos y condiciones de COFFE_APP (paso 3.3). Pública. */
@Component({
    selector: 'app-terms',
    imports: [RouterLink, IconComponent, LegalLinksComponent],
    templateUrl: './terms.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './legal-page.css'
})
export class TermsComponent {
  readonly info = LEGAL_INFO;
  readonly todo = isTodo;
}
