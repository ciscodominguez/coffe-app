import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { LEGAL_INFO, isTodo } from './legal-info';
import { LegalLinksComponent } from './legal-links.component';

/** Política de privacidad de COFFE_APP (paso 3.3, Ley 25.326). Pública. */
@Component({
    selector: 'app-privacy',
    imports: [RouterLink, IconComponent, LegalLinksComponent],
    templateUrl: './privacy.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './legal-page.css'
})
export class PrivacyComponent {
  readonly info = LEGAL_INFO;
  readonly todo = isTodo;
}
