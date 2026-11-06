import { Injectable, computed, signal } from '@angular/core';
import { UserRole } from '../models/auth.model';

export interface TourStep {
  /** Valor del atributo `data-tour` del elemento a señalar. Sin target, el paso va centrado. */
  target?: string;
  title: string;
  text: string;
}

const DONE_KEY_PREFIX = 'cofe_app_tour_done_';

const MANAGEMENT: readonly UserRole[] = ['OWNER', 'ADMIN', 'SUPERVISOR'];

/**
 * Recorrido de bienvenida: la primera vez que alguien entra, le señala las
 * partes principales de la app según su rol. Se puede volver a ver desde el
 * botón de ayuda del menú. "Ya lo vio" se guarda por usuario en ese
 * dispositivo.
 */
@Injectable({ providedIn: 'root' })
export class TourService {
  readonly steps = signal<TourStep[]>([]);
  readonly index = signal(0);
  readonly active = computed(() => this.steps().length > 0);
  readonly current = computed(() => this.steps()[this.index()] ?? null);
  readonly isLast = computed(() => this.index() === this.steps().length - 1);

  private userId: string | null = null;

  /** Arranca solo si esta persona todavía no lo vio en este dispositivo. */
  startIfFirstTime(userId: string, role: UserRole, firstName: string): void {
    if (this.hasSeen(userId)) return;
    // Un respiro para que el menú termine de dibujarse antes de señalarlo.
    setTimeout(() => this.start(userId, role, firstName), 600);
  }

  start(userId: string, role: UserRole, firstName: string): void {
    this.userId = userId;
    this.index.set(0);
    this.steps.set(this.stepsFor(role, firstName));
  }

  next(): void {
    if (this.isLast()) {
      this.finish();
    } else {
      this.index.set(this.index() + 1);
    }
  }

  prev(): void {
    if (this.index() > 0) this.index.set(this.index() - 1);
  }

  finish(): void {
    if (this.userId) {
      try {
        localStorage.setItem(DONE_KEY_PREFIX + this.userId, '1');
      } catch {
        // Sin localStorage: se vuelve a mostrar la próxima vez, no pasa nada grave.
      }
    }
    this.steps.set([]);
    this.index.set(0);
  }

  private hasSeen(userId: string): boolean {
    try {
      return localStorage.getItem(DONE_KEY_PREFIX + userId) === '1';
    } catch {
      return true;
    }
  }

  private stepsFor(role: UserRole, firstName: string): TourStep[] {
    const isKitchen = role === 'KITCHEN';
    const isManagement = MANAGEMENT.includes(role);
    const hello = firstName ? `¡Hola, ${firstName}!` : '¡Hola!';

    const steps: TourStep[] = [
      {
        title: `${hello} Bienvenido/a a COFFE_APP`,
        text: 'Te muestro en menos de un minuto dónde está cada cosa. Podés saltearlo cuando quieras.',
      },
      {
        target: 'nav-inicio',
        title: 'Inicio',
        text: isManagement
          ? 'Tu resumen del día: ventas, lo más vendido, alertas de stock y los primeros pasos para dejar todo listo.'
          : 'Tus accesos rápidos. Desde acá llegás a lo que más usás con un toque.',
      },
    ];

    if (!isKitchen) {
      steps.push({
        target: 'nav-pedidos',
        title: 'Tomar un pedido',
        text: 'Tres pasos: elegís la mesa (o "para llevar"), tocás los productos y lo mandás a cocina. En "Pedidos abiertos" cobrás e imprimís el ticket.',
      });
    }

    steps.push({
      target: 'nav-cocina',
      title: 'Cocina / Barra',
      text: isKitchen
        ? 'Acá entran los pedidos solos, en tiempo real. Tocá el botón grande de cada pedido para avanzarlo: Empezar → Listo.'
        : 'La pantalla de cocina: los pedidos entran solos y se ven por color según cuánto hace que esperan.',
    });

    if (!isKitchen) {
      steps.push({
        target: 'nav-caja',
        title: 'Caja',
        text: 'Abrí la caja al empezar el turno con el fondo inicial. Al terminar la cerrás contando los billetes y la app te dice si falta o sobra.',
      });
    }

    if (isManagement) {
      steps.push({
        target: 'nav-catalogo',
        title: 'Tu carta y tu equipo',
        text: 'En Productos, Categorías, Mesas y Usuarios cargás todo lo que el equipo necesita para trabajar.',
      });
    }

    steps.push(
      {
        target: 'theme-toggle',
        title: 'Modo claro u oscuro',
        text: 'Elegí cómo preferís verlo. Queda guardado en este dispositivo.',
      },
      {
        target: 'help-button',
        title: '¿Te perdiste?',
        text: 'Tocá acá cuando quieras para volver a ver este recorrido.',
      },
    );

    return steps;
  }
}
