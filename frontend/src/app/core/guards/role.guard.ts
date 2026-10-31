import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { UserRole } from '../models/auth.model';

/**
 * Fábrica de guard parametrizada por rol: deja pasar solo a usuarios cuyo
 * rol esté en `allowedRoles`. Se usa en la config de rutas: `canActivate: [roleGuard(['OWNER', 'ADMIN'])]`.
 */
export function roleGuard(allowedRoles: readonly UserRole[]): CanActivateFn {
  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    const user = authService.user();
    if (!user) {
      router.navigate(['/login']);
      return false;
    }
    if (!allowedRoles.includes(user.role as UserRole)) {
      router.navigate(['/']);
      return false;
    }
    return true;
  };
}
