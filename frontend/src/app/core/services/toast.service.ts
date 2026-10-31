import { Injectable, signal } from '@angular/core';

export type ToastKind = 'success' | 'error' | 'info';

export interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
}

const DURATION_MS: Readonly<Record<ToastKind, number>> = {
  success: 3000,
  info: 4000,
  error: 6000,
};

/**
 * Avisos cortos que aparecen abajo ("Pedido enviado", "Cobro registrado")
 * y se van solos. Los muestra ToastHostComponent (en AppComponent), así
 * cualquier pantalla puede avisar algo con una sola línea.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private seq = 0;
  readonly toasts = signal<Toast[]>([]);

  success(message: string): void {
    this.show('success', message);
  }

  error(message: string): void {
    this.show('error', message);
  }

  info(message: string): void {
    this.show('info', message);
  }

  dismiss(id: number): void {
    this.toasts.set(this.toasts().filter((toast) => toast.id !== id));
  }

  private show(kind: ToastKind, message: string): void {
    const id = ++this.seq;
    // Máximo 3 a la vista: el más viejo se va primero.
    this.toasts.set([...this.toasts().slice(-2), { id, kind, message }]);
    setTimeout(() => this.dismiss(id), DURATION_MS[kind]);
  }
}
