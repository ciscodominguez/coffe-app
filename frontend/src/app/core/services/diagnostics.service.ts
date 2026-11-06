import { ErrorHandler, Injectable, inject, signal } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

/** Misma clave que lee el aviso de emergencia de index.html. */
export const LAST_ERROR_KEY = 'coffe.lastError';

export interface RecordedError {
  message: string;
  at: string;
}

/**
 * V18 — guarda el último error que vio la app (de JavaScript o de una
 * llamada al servidor que falló) para mandarlo junto con el pedido de
 * ayuda del botón de soporte. Así Fran sabe qué pasó sin preguntar.
 */
@Injectable({ providedIn: 'root' })
export class DiagnosticsService {
  readonly lastError = signal<RecordedError | null>(read());

  constructor() {
    if (typeof window === 'undefined') return;
    window.addEventListener('unhandledrejection', (event) => this.record(describe(event.reason)));
    window.addEventListener('error', (event) => this.record(describe(event.error ?? event.message)));
  }

  record(message: string): void {
    const entry = { message: message.slice(0, 500), at: new Date().toISOString() };
    this.lastError.set(entry);
    try {
      localStorage.setItem(LAST_ERROR_KEY, JSON.stringify(entry));
    } catch {
      /* sin almacenamiento */
    }
  }

  /** El último error, si fue hace menos de una hora (uno viejo confunde más de lo que ayuda). */
  recent(): RecordedError | null {
    const e = this.lastError();
    return e && Date.now() - new Date(e.at).getTime() < 60 * 60_000 ? e : null;
  }
}

/** Reemplaza al ErrorHandler de Angular: muestra el error en la consola como siempre y lo anota. */
@Injectable()
export class DiagnosticsErrorHandler extends ErrorHandler {
  private readonly diagnostics = inject(DiagnosticsService);

  override handleError(error: unknown): void {
    super.handleError(error);
    this.diagnostics.record(describe(error));
  }
}

/** Anota las llamadas al servidor que fallaron por algo nuestro (caído, sin conexión, error 5xx). */
export const diagnosticsInterceptor: HttpInterceptorFn = (req, next) => {
  const diagnostics = inject(DiagnosticsService);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500)) {
        const path = req.url.replace(/^https?:\/\/[^/]+/, '').split('?')[0];
        const what = error.status === 0 ? 'sin respuesta del servidor' : `error ${error.status}`;
        const detail = (error.error as { message?: unknown } | null)?.message;
        diagnostics.record(`${req.method} ${path}: ${what}${typeof detail === 'string' ? ' — ' + detail : ''}`);
      }
      return throwError(() => error);
    }),
  );
};

function describe(error: unknown): string {
  if (error instanceof Error) return `${error.name}: ${error.message}`;
  if (typeof error === 'string') return error;
  try {
    return JSON.stringify(error);
  } catch {
    return String(error);
  }
}

function read(): RecordedError | null {
  try {
    const raw = localStorage.getItem(LAST_ERROR_KEY);
    return raw ? (JSON.parse(raw) as RecordedError) : null;
  } catch {
    return null;
  }
}
