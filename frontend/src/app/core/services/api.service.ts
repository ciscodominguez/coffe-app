import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Cliente HTTP genérico para el resto de la API. La inyección del Bearer
 * token y el manejo de 401 NO están acá: en Angular el lugar idiomático
 * para eso es un `HttpInterceptorFn` (ver `auth.interceptor.ts`), que
 * aplica a TODA request de `HttpClient` sin que cada consumidor de este
 * service tenga que acordarse de nada.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly baseUrl = environment.apiBaseUrl.replace(/\/+$/, '');

  constructor(private readonly http: HttpClient) {}

  get<T>(path: string, query?: Record<string, string | number | boolean | undefined>): Promise<T> {
    return firstValueFrom(this.http.get<T>(this.url(path), { params: this.buildParams(query) }));
  }

  post<T>(path: string, body?: unknown): Promise<T> {
    return firstValueFrom(this.http.post<T>(this.url(path), body ?? {}));
  }

  patch<T>(path: string, body?: unknown): Promise<T> {
    return firstValueFrom(this.http.patch<T>(this.url(path), body ?? {}));
  }

  /** Descarga un archivo (ej. el Excel de Reportes) con el token de la sesión. */
  getBlob(path: string, query?: Record<string, string | number | boolean | undefined>): Promise<Blob> {
    return firstValueFrom(this.http.get(this.url(path), { params: this.buildParams(query), responseType: 'blob' }));
  }

  put<T>(path: string, body?: unknown): Promise<T> {
    return firstValueFrom(this.http.put<T>(this.url(path), body ?? {}));
  }

  delete<T>(path: string): Promise<T> {
    return firstValueFrom(this.http.delete<T>(this.url(path)));
  }

  private url(path: string): string {
    return `${this.baseUrl}${path.startsWith('/') ? path : `/${path}`}`;
  }

  private buildParams(query?: Record<string, string | number | boolean | undefined>): HttpParams {
    let params = new HttpParams();
    if (!query) return params;
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined) params = params.set(key, String(value));
    }
    return params;
  }
}
