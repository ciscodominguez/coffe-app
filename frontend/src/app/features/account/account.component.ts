import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { USER_ROLE_LABELS, UserRole } from '../../core/models/auth.model';
import { AuthService } from '../../core/services/auth.service';
import { extractErrorMessage } from '../../core/services/error.util';
import { ThemeService, ThemePreference } from '../../core/services/theme.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { LegalLinksComponent } from '../legal/legal-links.component';

/**
 * "Mi cuenta" (V9): datos de quien está logueado, cambiar la propia
 * contraseña (pide la actual) y la preferencia de tema. Al cambiar la
 * contraseña, el backend cierra las sesiones en los demás dispositivos y
 * este sigue adentro con un token nuevo.
 */
@Component({
    selector: 'app-account',
    imports: [ReactiveFormsModule, IconComponent, LegalLinksComponent],
    templateUrl: './account.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './account.component.css'
})
export class AccountComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly toast = inject(ToastService);
  readonly theme = inject(ThemeService);

  readonly user = this.authService.user;
  readonly roleLabel = computed(() => {
    const role = this.user()?.role as UserRole | undefined;
    return role ? USER_ROLE_LABELS[role] ?? role : '';
  });
  readonly initials = computed(() => {
    const u = this.user();
    return u ? `${u.firstName?.[0] ?? ''}${u.lastName?.[0] ?? ''}`.toUpperCase() : '';
  });

  readonly themeOptions: readonly { value: ThemePreference; label: string }[] = [
    { value: 'light', label: 'Claro' },
    { value: 'dark', label: 'Oscuro' },
    { value: 'system', label: 'Como el dispositivo' },
  ];

  readonly form = this.fb.nonNullable.group({
    current: ['', [Validators.required]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    repeat: ['', [Validators.required]],
  });

  readonly saving = signal(false);
  readonly showPasswords = signal(false);
  readonly errorMessage = signal<string | null>(null);
  private readonly values = signal(this.form.getRawValue());
  readonly mismatch = computed(() => {
    const { password, repeat } = this.values();
    return repeat.length > 0 && password !== repeat;
  });

  constructor() {
    this.form.valueChanges.subscribe(() => this.values.set(this.form.getRawValue()));
  }

  async changePassword(): Promise<void> {
    if (this.form.invalid || this.mismatch()) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.saving()) return;
    this.saving.set(true);
    this.errorMessage.set(null);
    try {
      const { current, password } = this.form.getRawValue();
      await this.authService.changePassword(current, password);
      this.form.reset({ current: '', password: '', repeat: '' });
      this.toast.success('Contraseña cambiada. Se cerró tu sesión en los otros dispositivos.');
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudo cambiar la contraseña.'));
    } finally {
      this.saving.set(false);
    }
  }
}
