import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, AuthUserDto, LoginInput, RegisterBusinessInput } from '../models/auth.model';

const STORAGE_KEY = 'cofe_app_session_v1';

interface StoredSession {
  accessToken: string;
  user: AuthUserDto;
}

/**
 * Store de la sesión (token + usuario). Usa `localStorage` (no
 * `sessionStorage`) a propósito — la sesión de un dispositivo (la compu
 * del dueño/administrador) debería sobrevivir un refresh del navegador sin
 * forzar un re-login.
 *
 * Se implementa como un service de Angular con signals en vez de un store
 * manual con listeners a mano — `signal`/`computed` ya le dan a cualquier
 * componente standalone reactividad automática sin suscribirse a nada.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly session = signal<StoredSession | null>(this.readFromStorage());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly isAuthenticated = computed(() => this.session() !== null);

  constructor(private readonly http: HttpClient) {}

  getAccessToken(): string | null {
    return this.session()?.accessToken ?? null;
  }

  async login(input: LoginInput): Promise<AuthUserDto> {
    const response = await firstValueFrom(
      this.http.post<AuthResponse>(`${environment.apiBaseUrl}/auth/login`, input),
    );
    this.setSession({ accessToken: response.accessToken, user: response.user });
    return response.user;
  }

  async registerBusiness(input: RegisterBusinessInput): Promise<AuthUserDto> {
    const response = await firstValueFrom(
      this.http.post<AuthResponse>(`${environment.apiBaseUrl}/auth/register-business`, input),
    );
    this.setSession({ accessToken: response.accessToken, user: response.user });
    return response.user;
  }

  /**
   * V9 — cambiar la propia contraseña. El backend cierra las demás sesiones
   * y devuelve un token nuevo para seguir adentro en este dispositivo.
   */
  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    const response = await firstValueFrom(
      this.http.post<AuthResponse>(`${environment.apiBaseUrl}/auth/change-password`, { currentPassword, newPassword }),
    );
    this.setSession({ accessToken: response.accessToken, user: response.user });
  }

  /** V9 — "Olvidé mi contraseña": el backend responde igual exista o no el email. */
  async forgotPassword(email: string): Promise<void> {
    await firstValueFrom(this.http.post<void>(`${environment.apiBaseUrl}/auth/forgot-password`, { email }));
  }

  /** V9 — elegir la contraseña nueva con el token del link del mail. */
  async resetPassword(token: string, newPassword: string): Promise<void> {
    await firstValueFrom(this.http.post<void>(`${environment.apiBaseUrl}/auth/reset-password`, { token, newPassword }));
  }

  /**
   * Se llama sola ante un 401 de cualquier request (ver
   * `authInterceptor`), y también manualmente desde un botón "Cerrar
   * sesión".
   */
  logout(): void {
    this.setSession(null);
  }

  private setSession(session: StoredSession | null): void {
    this.session.set(session);
    try {
      if (session) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
      } else {
        localStorage.removeItem(STORAGE_KEY);
      }
    } catch {
      // localStorage bloqueado (navegación privada, cuota llena) — la
      // sesión sigue viva en memoria para esta pestaña.
    }
  }

  private readFromStorage(): StoredSession | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? (JSON.parse(raw) as StoredSession) : null;
    } catch {
      return null;
    }
  }
}
