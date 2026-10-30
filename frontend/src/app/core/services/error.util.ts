import { HttpErrorResponse } from '@angular/common/http';

/**
 * El backend devuelve `{ statusCode, message, error }`, con `message` como
 * string o array de strings (uno por campo inválido) — ver
 * `GlobalExceptionHandler` del backend. Si es un array se unen los
 * mensajes; si no hay mensaje usable, se cae al `fallback` con el status HTTP.
 */
export function extractErrorMessage(error: unknown, fallback = 'Ocurrió un error inesperado.'): string {
  if (error instanceof HttpErrorResponse) {
    const message = (error.error as { message?: string | string[] } | null)?.message;
    if (Array.isArray(message)) return message.join(' ');
    if (typeof message === 'string' && message.length > 0) return message;
    return `${fallback} (HTTP ${error.status})`;
  }
  if (error instanceof Error) return error.message;
  return fallback;
}
