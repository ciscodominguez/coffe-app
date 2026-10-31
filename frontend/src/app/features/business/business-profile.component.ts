import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { BusinessResponse } from '../../core/models/business.model';
import { BusinessService } from '../../core/services/business.service';
import { extractErrorMessage } from '../../core/services/error.util';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

const FISCAL_CONDITIONS = ['MONOTRIBUTO', 'RESPONSABLE_INSCRIPTO', 'EXENTO'] as const;

const FISCAL_LABELS: Readonly<Record<string, string>> = {
  MONOTRIBUTO: 'Monotributo',
  RESPONSABLE_INSCRIPTO: 'Responsable Inscripto',
  EXENTO: 'Exento',
};

@Component({
    selector: 'app-business-profile',
    imports: [CommonModule, ReactiveFormsModule, IconComponent],
    templateUrl: './business-profile.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './business-profile.component.css'
})
export class BusinessProfileComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly businessService = inject(BusinessService);
  private readonly authService = inject(AuthService);

  readonly fiscalConditions = FISCAL_CONDITIONS;
  readonly fiscalLabels = FISCAL_LABELS;
  private readonly toast = inject(ToastService);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly business = signal<BusinessResponse | null>(null);

  /** PATCH /businesses/me exige rol OWNER — ver BusinessController del backend. */
  readonly canEdit = () => this.authService.user()?.role === 'OWNER';

  readonly form = this.fb.nonNullable.group({
    legalName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(150)]],
    tradeName: ['', [Validators.maxLength(150)]],
    fiscalCondition: ['MONOTRIBUTO' as (typeof FISCAL_CONDITIONS)[number], [Validators.required]],
  });

  async ngOnInit(): Promise<void> {
    try {
      const business = await this.businessService.getMe();
      this.business.set(business);
      this.form.patchValue({
        legalName: business.legalName,
        tradeName: business.tradeName ?? '',
        fiscalCondition: business.fiscalCondition as (typeof FISCAL_CONDITIONS)[number],
      });
      if (!this.canEdit()) this.form.disable();
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudo cargar el negocio.'));
    } finally {
      this.loading.set(false);
    }
  }

  async onSubmit(): Promise<void> {
    if (this.form.invalid || this.saving()) return;

    this.saving.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    try {
      const updated = await this.businessService.updateMe(this.form.getRawValue());
      this.business.set(updated);
      this.successMessage.set('Datos del negocio actualizados.');
      this.form.markAsPristine();
      this.toast.success('Datos del negocio guardados.');
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudo guardar.'));
    } finally {
      this.saving.set(false);
    }
  }
}
