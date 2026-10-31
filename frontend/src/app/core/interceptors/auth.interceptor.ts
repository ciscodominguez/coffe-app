import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Interceptor funcional (`HttpInterceptorFn`, Angular 15+) que agrega el
 * Bearer token y maneja los 401 (cierra la sesión local). Se
 * aplica UNA sola vez, de forma centralizada, a toda request de
 * `HttpClient` (ver el `provideHttpClient(withInterceptors([...]))` en
 * `app.config.ts`) — nadie tiene que acordarse de pasarlo a mano.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const token = authService.getAccessToken();
  const authorizedReq = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(authorizedReq).pipe(
    catchError((error: unknown) => {
      // Solo si había sesión: un 401 del propio login (contraseña mal) no es "sesión vencida".
      if (error instanceof HttpErrorResponse && error.status === 401 && token && !req.url.includes('/auth/login')) {
        // Un 401 en cualquier request "normal" significa que el token ya
        // no sirve — se cierra la sesión local para que el guard de ruta
        // mande al login.
        // V9: también llega acá si le cambiaron la contraseña o lo dieron de
        // baja (el backend invalida las sesiones abiertas al instante).
        // V15: si el café fue suspendido, el backend manda el motivo y a
        // quién llamar — se lo mostramos tal cual en el login.
        const message = (error.error as { message?: unknown } | null)?.message;
        const blocked = typeof message === 'string' && /suspendido|dado de baja/i.test(message);
        authService.logout();
        router.navigate(['/login'], {
          queryParams: { sesion: blocked ? 'bloqueada' : 'cerrada' },
          state: blocked ? { notice: message } : undefined,
        });
      }
      // V16 — café suspendido por falta de pago: el dueño sigue adentro,
      // pero solo puede usar la pantalla de Suscripción (para pagar).
      if (error instanceof HttpErrorResponse && error.status === 402 && token) {
        if (!router.url.startsWith('/suscripcion')) router.navigate(['/suscripcion']);
      }
      return throwError(() => error);
    }),
  );
};
