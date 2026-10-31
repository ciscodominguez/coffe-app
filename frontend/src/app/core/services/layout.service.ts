import { Injectable, signal } from '@angular/core';

/**
 * Estado de la "cáscara" de la app que las pantallas pueden cambiar. Hoy:
 * el modo pantalla completa de Cocina (para un TV o monitor fijo), que
 * esconde el menú y agranda todo.
 */
@Injectable({ providedIn: 'root' })
export class LayoutService {
  readonly immersive = signal(false);

  async enterImmersive(): Promise<void> {
    this.immersive.set(true);
    try {
      if (!document.fullscreenElement) await document.documentElement.requestFullscreen();
    } catch {
      // Algunos navegadores (iOS) no dejan pantalla completa: igual se esconde el menú.
    }
  }

  async exitImmersive(): Promise<void> {
    this.immersive.set(false);
    try {
      if (document.fullscreenElement) await document.exitFullscreen();
    } catch {
      // Ignorado — ver enterImmersive.
    }
  }
}
