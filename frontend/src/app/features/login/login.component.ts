import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { SUPERADMIN_ROLE } from '../../core/models/auth.model';
import { AuthService } from '../../core/services/auth.service';
import { extractErrorMessage } from '../../core/services/error.util';
import { ThemeService } from '../../core/services/theme.service';
import { LegalLinksComponent } from '../legal/legal-links.component';
import { SupportSheetComponent } from '../../shared/components/support/support-sheet.component';
import { IconComponent } from '../../shared/components/icon/icon.component';

/**
 * Ingreso. Reactive Forms con `inject()` (el FormBuilder se usa en el
 * inicializador del campo `form`, antes del cuerpo del constructor).
 */
@Component({
    selector: 'app-login',
    imports: [ReactiveFormsModule, RouterLink, IconComponent, LegalLinksComponent, SupportSheetComponent],
    templateUrl: './login.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './login.component.css'
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  readonly theme = inject(ThemeService);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly showPassword = signal(false);
  /** V18 — "¿Problemas para entrar?": la misma ventana del botón Soporte. */
  readonly supportOpen = signal(false);

  /** Aviso arriba del formulario según de dónde viene (sesión cerrada, contraseña restablecida). */
  readonly notice = signal<{ kind: 'info' | 'success' | 'warning'; text: string } | null>(null);

  constructor() {
    const params = inject(ActivatedRoute).snapshot.queryParamMap;
    const blockedText = (this.router.getCurrentNavigation()?.extras.state?.['notice'] ??
      (history.state as { notice?: string } | null)?.notice) as string | undefined;
    if (params.get('sesion') === 'bloqueada') {
      // V15 — café suspendido: el texto viene del backend (motivo + contacto).
      this.notice.set({
        kind: 'warning',
        text: blockedText ?? 'Este café está suspendido. Comunicate con quien te dio acceso a COFFE_APP.',
      });
    } else if (params.get('sesion') === 'cerrada') {
      this.notice.set({
        kind: 'info',
        text: 'Tu sesión venció o se cerró (por ejemplo, si cambiaron tu contraseña). Volvé a ingresar.',
      });
    } else if (params.get('restablecida') === '1') {
      this.notice.set({ kind: 'success', text: 'Listo: ya podés ingresar con tu contraseña nueva.' });
    }
    const email = params.get('email');
    if (email) this.form.controls.email.setValue(email);
  }

  async onSubmit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.submitting()) return;

    this.submitting.set(true);
    this.errorMessage.set(null);

    try {
      const user = await this.authService.login(this.form.getRawValue());
      // Cada rol arranca donde más trabaja: cocina directo en su pantalla, el resto en Inicio.
      // V15 — la cuenta de superadmin va a su panel.
      this.router.navigate([user.role === SUPERADMIN_ROLE ? '/superadmin' : user.role === 'KITCHEN' ? '/kds' : '/']);
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudo iniciar sesión.'));
    } finally {
      this.submitting.set(false);
    }
  }
}
