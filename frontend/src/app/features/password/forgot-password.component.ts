import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { extractErrorMessage } from '../../core/services/error.util';
import { ThemeService } from '../../core/services/theme.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

/**
 * "Olvidé mi contraseña" (V9): se escribe el email y el backend manda un
 * link para elegir una contraseña nueva. Siempre muestra el mismo mensaje,
 * exista o no la cuenta (el backend tampoco lo revela).
 */
@Component({
    selector: 'app-forgot-password',
    imports: [ReactiveFormsModule, RouterLink, IconComponent],
    templateUrl: './forgot-password.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: '../login/login.component.css'
})
export class ForgotPasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  readonly theme = inject(ThemeService);

  readonly form = this.fb.nonNullable.group({
    email: [inject(ActivatedRoute).snapshot.queryParamMap.get('email') ?? '', [Validators.required, Validators.email]],
  });

  readonly submitting = signal(false);
  readonly sentTo = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  async onSubmit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.submitting()) return;
    this.submitting.set(true);
    this.errorMessage.set(null);
    try {
      const email = this.form.getRawValue().email.trim();
      await this.authService.forgotPassword(email);
      this.sentTo.set(email);
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudo enviar el link. Probá de nuevo.'));
    } finally {
      this.submitting.set(false);
    }
  }
}
