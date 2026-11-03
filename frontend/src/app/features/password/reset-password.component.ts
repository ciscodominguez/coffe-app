import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { extractErrorMessage } from '../../core/services/error.util';
import { ThemeService } from '../../core/services/theme.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

/**
 * Pantalla del link del mail (/restablecer?token=…) — V9. Se elige la
 * contraseña nueva (dos veces, para no equivocarse) y se vuelve al login.
 * Al restablecerla, el backend cierra todas las sesiones abiertas de esa
 * cuenta.
 */
@Component({
    selector: 'app-reset-password',
    imports: [ReactiveFormsModule, RouterLink, IconComponent],
    templateUrl: './reset-password.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: '../login/login.component.css'
})
export class ResetPasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  readonly theme = inject(ThemeService);

  readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token');

  readonly form = this.fb.nonNullable.group({
    password: ['', [Validators.required, Validators.minLength(8)]],
    repeat: ['', [Validators.required]],
  });

  readonly submitting = signal(false);
  readonly showPassword = signal(false);
  readonly errorMessage = signal<string | null>(null);
  private readonly values = signal(this.form.getRawValue());
  readonly mismatch = computed(() => {
    const { password, repeat } = this.values();
    return repeat.length > 0 && password !== repeat;
  });

  constructor() {
    this.form.valueChanges.subscribe(() => this.values.set(this.form.getRawValue()));
  }

  async onSubmit(): Promise<void> {
    if (this.form.invalid || this.mismatch()) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.submitting() || !this.token) return;
    this.submitting.set(true);
    this.errorMessage.set(null);
    try {
      await this.authService.resetPassword(this.token, this.form.getRawValue().password);
      this.router.navigate(['/login'], { queryParams: { restablecida: '1' } });
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudo cambiar la contraseña.'));
    } finally {
      this.submitting.set(false);
    }
  }
}
