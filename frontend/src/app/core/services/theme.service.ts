import { Injectable, computed, signal } from '@angular/core';

export type ThemePreference = 'light' | 'dark' | 'system';

/** Misma clave que el script inline de index.html (aplica el tema antes de que arranque Angular). */
const STORAGE_KEY = 'cofe_app_theme';

/**
 * Tema claro/oscuro. Cada persona elige el suyo (queda guardado en ese
 * dispositivo); "system" sigue lo que tenga configurado el celular o la
 * compu. El CSS global (styles.css) define los colores de los dos temas
 * con los mismos nombres de variables, así que acá solo se cambia el
 * atributo `data-theme` de <html>.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly media =
    typeof window !== 'undefined' && window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;

  readonly preference = signal<ThemePreference>(this.readPreference());
  private readonly systemDark = signal(this.media?.matches ?? false);

  readonly resolved = computed<'light' | 'dark'>(() => {
    const pref = this.preference();
    if (pref === 'system') return this.systemDark() ? 'dark' : 'light';
    return pref;
  });

  constructor() {
    this.media?.addEventListener('change', (event) => {
      this.systemDark.set(event.matches);
      this.apply();
    });
    this.apply();
  }

  setPreference(preference: ThemePreference): void {
    this.preference.set(preference);
    try {
      localStorage.setItem(STORAGE_KEY, preference);
    } catch {
      // Sin localStorage (modo privado): el tema vale solo para esta sesión.
    }
    this.apply();
  }

  /** Botón rápido del menú: alterna entre claro y oscuro. */
  toggle(): void {
    this.setPreference(this.resolved() === 'dark' ? 'light' : 'dark');
  }

  private apply(): void {
    if (typeof document === 'undefined') return;
    const theme = this.resolved();
    document.documentElement.setAttribute('data-theme', theme);
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme === 'dark' ? '#1e1a17' : '#9a4a1c');
  }

  private readPreference(): ThemePreference {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored === 'light' || stored === 'dark' || stored === 'system') return stored;
    } catch {
      // Ignorado a propósito — ver setPreference.
    }
    return 'system';
  }
}
