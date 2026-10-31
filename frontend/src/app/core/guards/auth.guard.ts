import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SUPERADMIN_ROLE } from '../models/auth.model';
import { AuthService } from '../services/auth.service';

/**
 * Guard de ruta que exige sesión iniciada, sin restricción por rol (eso lo
 * hace `roleGuard`). Redirige a `/login` sin sesión.
 *
 * Esto NO es la autorización real — el backend ya rechaza cada request
 * con 401/403 sin que este guard exista (ver `JwtAuthenticationFilter`/
 * `@PreAuthorize` del backend). Sirve solo para no mostrarle a alguien sin
 * sesión una pantalla protegida un instante antes de que el primer
 * request falle — pura UX, cero seguridad nueva.
 */
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    // V15 — el superadmin no tiene café: su lugar es el panel.
    if (authService.user()?.role === SUPERADMIN_ROLE) {
      router.navigate(['/superadmin']);
      return false;
    }
    return true;
  }

  router.navigate(['/login']);
  return false;
};

/** V15 — el panel de superadmin: solo la cuenta de Fran. */
export const superadminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const user = authService.user();
  if (!user) {
    router.navigate(['/login']);
    return false;
  }
  if (user.role !== SUPERADMIN_ROLE) {
    router.navigate(['/']);
    return false;
  }
  return true;
};
