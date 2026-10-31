import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { RegisterBusinessInput } from '../../core/models/auth.model';
import { AuthService } from '../../core/services/auth.service';
import { extractErrorMessage } from '../../core/services/error.util';
import { ThemeService } from '../../core/services/theme.service';
import { ToastService } from '../../core/services/toast.service';
import { LegalLinksComponent } from '../legal/legal-links.component';
import { IconComponent } from '../../shared/components/icon/icon.component';

type FiscalCondition = RegisterBusinessInput['fiscalCondition'];

const FISCAL_OPTIONS: readonly { value: FiscalCondition; label: string; text: string }[] = [
  { value: 'MONOTRIBUTO', label: 'Monotributo', text: 'La más común en cafés chicos.' },
  { value: 'RESPONSABLE_INSCRIPTO', label: 'Responsable Inscripto', text: 'Si facturás con IVA discriminado.' },
  { value: 'EXENTO', label: 'Exento', text: 'Si tu contador te lo indicó.' },
];

/**
 * Alta de un negocio nuevo (antes solo se podía pegándole directo a la
 * API): dos pasos cortos — los datos del café y la cuenta de quien lo
 * administra. Al terminar entra directo, y el Inicio le muestra los
 * "Primeros pasos" para dejar todo listo.
 */
@Component({
    selector: 'app-register',
    imports: [ReactiveFormsModule, RouterLink, IconComponent, LegalLinksComponent],
    templateUrl: './register.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: '../login/login.component.css'
})
export class RegisterComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  readonly theme = inject(ThemeService);

  readonly fiscalOptions = FISCAL_OPTIONS;
  readonly step = signal<1 | 2>(1);
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly showPassword = signal(false);

  readonly businessForm = this.fb.nonNullable.group({
    tradeName: ['', [Validators.required, Validators.maxLength(150)]],
    legalName: ['', [Validators.required, Validators.maxLength(150)]],
    taxId: ['', [Validators.required, Validators.pattern(/^\d{2}-?\d{8}-?\d$/)]],
    fiscalCondition: ['MONOTRIBUTO' as FiscalCondition, [Validators.required]],
  });

  readonly accountForm = this.fb.nonNullable.group({
    ownerFirstName: ['', [Validators.required]],
    ownerLastName: ['', [Validators.required]],
    ownerEmail: ['', [Validators.required, Validators.email]],
    ownerPassword: ['', [Validators.required, Validators.minLength(8)]],
    // V17 — casilla obligatoria: "Leí y acepto los Términos y la Política de privacidad".
    acceptTerms: [false, [Validators.requiredTrue]],
  });

  next(): void {
    if (this.businessForm.invalid) {
      this.businessForm.markAllAsTouched();
      return;
    }
    this.step.set(2);
  }

  /** Si todavía no escribió la razón social, se completa con el nombre del café (se puede cambiar). */
  syncLegalName(): void {
    const { tradeName, legalName } = this.businessForm.getRawValue();
    if (!legalName && tradeName) this.businessForm.controls.legalName.setValue(tradeName);
  }

  async submit(): Promise<void> {
    if (this.accountForm.invalid) {
      this.accountForm.markAllAsTouched();
      return;
    }
    if (this.submitting()) return;
    this.submitting.set(true);
    this.errorMessage.set(null);
    try {
      const business = this.businessForm.getRawValue();
      const account = this.accountForm.getRawValue();
      await this.authService.registerBusiness({
        ...business,
        taxId: this.formatTaxId(business.taxId),
        ...account,
      });
      this.toast.success('¡Listo! Tu café ya está en COFFE_APP.');
      this.router.navigate(['/']);
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudo registrar el negocio.'));
    } finally {
      this.submitting.set(false);
    }
  }

  /** "20111111111" → "20-11111111-1" (mismo formato con guiones que usa el resto del sistema). */
  private formatTaxId(raw: string): string {
    const digits = raw.replace(/\D/g, '');
    return digits.length === 11 ? `${digits.slice(0, 2)}-${digits.slice(2, 10)}-${digits.slice(10)}` : raw;
  }
}
