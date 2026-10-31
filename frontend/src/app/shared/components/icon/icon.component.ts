import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Círculo como `path` (así cada ícono es solo una lista de trazos). */
function c(cx: number, cy: number, r: number): string {
  return `M${cx - r} ${cy}a${r} ${r} 0 1 0 ${2 * r} 0a${r} ${r} 0 1 0 ${-2 * r} 0`;
}

/**
 * Íconos propios, dibujados a mano en una grilla de 24×24 con trazo (sin
 * relleno), para no sumar ninguna librería. Cada entrada es la lista de
 * trazos `d` del SVG.
 */
const ICONS = {
  home: ['M3 11 12 4l9 7', 'M5 10v10h14V10', 'M10 20v-5h4v5'],
  receipt: ['M6 3h12v18l-3-2-3 2-3-2-3 2z', 'M9 8h6', 'M9 12h6', 'M9 16h4'],
  flame: ['M12 3c2 3 5 5 5 9a5 5 0 0 1-10 0c0-2 1-3.5 2-4.5 0 2 1 3 2 3 0-3-1-5 1-7.5z'],
  wallet: ['M3 7h16a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z', 'M3 7l12-3v3', c(16, 13.5, 1.2)],
  coffee: ['M4 9h12v5a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5z', 'M16 10h1.5a2.5 2.5 0 0 1 0 5H16', 'M8 3v3', 'M12 3v3'],
  tag: ['M3 12V4h8l10 10-8 8z', c(7.5, 7.5, 1.2)],
  box: ['M3 7l9-4 9 4v10l-9 4-9-4z', 'M3 7l9 4 9-4', 'M12 11v10'],
  table: ['M3 8h18', 'M6 8v11', 'M18 8v11', 'M6 13h12'],
  users: [c(9, 8, 3.5), 'M2.5 20a6.5 6.5 0 0 1 13 0', 'M16 4.5a3.5 3.5 0 0 1 0 7', 'M18 14.5a6 6 0 0 1 3.5 5.5'],
  store: ['M4 10v10h16V10', 'M3 10l2-6h14l2 6', 'M3 10a3 3 0 0 0 6 0 3 3 0 0 0 6 0 3 3 0 0 0 6 0', 'M10 20v-5h4v5'],
  building: ['M4 21V5l8-2v18', 'M12 8h8v13', 'M3 21h18', 'M7 8h2', 'M7 12h2', 'M7 16h2', 'M15 12h2', 'M15 16h2'],
  sun: [
    c(12, 12, 4),
    'M12 2v2',
    'M12 20v2',
    'M4.9 4.9l1.4 1.4',
    'M17.7 17.7l1.4 1.4',
    'M2 12h2',
    'M20 12h2',
    'M4.9 19.1l1.4-1.4',
    'M17.7 6.3l1.4-1.4',
  ],
  moon: ['M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z'],
  help: [c(12, 12, 9), 'M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.6v.6', 'M12 17h.01'],
  logout: ['M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3', 'M10 17l5-5-5-5', 'M15 12H4'],
  plus: ['M12 5v14', 'M5 12h14'],
  minus: ['M5 12h14'],
  trash: ['M4 7h16', 'M9 7V4h6v3', 'M6 7l1 13h10l1-13'],
  printer: ['M7 9V3h10v6', 'M17 16h4v-5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v5h4', 'M7 14h10v7H7z'],
  check: ['M5 12.5l4.5 4.5L19 7'],
  x: ['M6 6l12 12', 'M18 6L6 18'],
  search: [c(11, 11, 6.5), 'M16 16l4.5 4.5'],
  'arrow-left': ['M19 12H5', 'M11 6l-6 6 6 6'],
  'arrow-right': ['M5 12h14', 'M13 6l6 6-6 6'],
  'chevron-right': ['M9 6l6 6-6 6'],
  'chevron-down': ['M6 9l6 6 6-6'],
  menu: ['M4 6h16', 'M4 12h16', 'M4 18h16'],
  more: [c(5, 12, 1), c(12, 12, 1), c(19, 12, 1)],
  grid: ['M4 4h7v7H4z', 'M13 4h7v7h-7z', 'M4 13h7v7H4z', 'M13 13h7v7h-7z'],
  maximize: ['M4 9V4h5', 'M15 4h5v5', 'M20 15v5h-5', 'M9 20H4v-5'],
  minimize: ['M9 4v5H4', 'M20 9h-5V4', 'M15 20v-5h5', 'M4 15h5v5'],
  alert: ['M12 3l10 18H2z', 'M12 10v4', 'M12 17.5h.01'],
  info: [c(12, 12, 9), 'M12 11v5', 'M12 7.5h.01'],
  edit: ['M4 20h4L19 9l-4-4L4 16z', 'M13.5 6.5l4 4'],
  clock: [c(12, 12, 9), 'M12 7v5l3 2'],
  chart: ['M4 20V4', 'M4 20h16', 'M8 16v-4', 'M12 16V8', 'M16 16v-6'],
  card: ['M3 6h18v12H3z', 'M3 10h18', 'M7 15h3'],
  cash: ['M2 7h20v10H2z', c(12, 12, 2.5), 'M6 12h.01', 'M18 12h.01'],
  qr: ['M4 4h6v6H4z', 'M14 4h6v6h-6z', 'M4 14h6v6H4z', 'M14 14h2v2h-2z', 'M18 14h2', 'M14 18h2v2', 'M18 18h2v2h-2z'],
  transfer: ['M4 8h14l-3-3', 'M20 16H6l3 3'],
  bag: ['M5 8h14l-1 12H6z', 'M9 8V6a3 3 0 0 1 6 0v2'],
  note: ['M4 4h12l4 4v12H4z', 'M8 12h8', 'M8 16h5'],
  sparkles: [
    'M12 3l1.8 4.7 4.7 1.8-4.7 1.8L12 16l-1.8-4.7-4.7-1.8 4.7-1.8z',
    'M19 15l.8 2.2 2.2.8-2.2.8L19 21l-.8-2.2-2.2-.8 2.2-.8z',
  ],
  'wifi-off': ['M3 3l18 18', 'M8.5 16.5a5 5 0 0 1 7 0', 'M5 12.9a10 10 0 0 1 5.2-2.7', 'M19 12.9a10 10 0 0 0-2.3-1.6', 'M12 20h.01'],
  refresh: ['M20 11a8 8 0 0 0-14.8-4', 'M4 3v4h4', 'M4 13a8 8 0 0 0 14.8 4', 'M20 21v-4h-4'],
  eye: ['M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z', c(12, 12, 3)],
  percent: ['M19 5L5 19', c(7, 7, 2.5), c(17, 17, 2.5)],
  sliders: ['M4 6h9', 'M17 6h3', 'M4 12h3', 'M11 12h9', 'M4 18h11', 'M19 18h1', c(15, 6, 2), c(9, 12, 2), c(17, 18, 2)],
  list: ['M8 6h12', 'M8 12h12', 'M8 18h12', 'M4 6h.01', 'M4 12h.01', 'M4 18h.01'],
  image: ['M4 5h16v14H4z', c(9, 10, 1.5), 'M20 16l-5-5-8 8'],
  send: ['M4 12l16-8-6 16-2-6z', 'M12 14l8-10'],
  lock: ['M5 11h14v10H5z', 'M8 11V7a4 4 0 0 1 8 0v4'],
  map: ['M3 6l6-3 6 3 6-3v15l-6 3-6-3-6 3z', 'M9 3v15', 'M15 6v15'],
  whatsapp: ['M20 12a8 8 0 0 1-11.8 7L4 20l1.1-4.1A8 8 0 1 1 20 12z', 'M9 9.5c0 3 2.5 5.5 5.5 5.5'],
  lifebuoy: [c(12, 12, 9), c(12, 12, 4), 'M5.6 5.6l3.6 3.6', 'M14.8 14.8l3.6 3.6', 'M18.4 5.6l-3.6 3.6', 'M9.2 14.8l-3.6 3.6'],
  settings: [
    c(12, 12, 3),
    'M12 2v3',
    'M12 19v3',
    'M2 12h3',
    'M19 12h3',
    'M4.9 4.9l2.1 2.1',
    'M17 17l2.1 2.1',
    'M4.9 19.1L7 17',
    'M17 7l2.1-2.1',
  ],
} as const;

export type IconName = keyof typeof ICONS;

/**
 * `<app-icon name="coffee" />` — hereda el color del texto (`currentColor`).
 * Decorativo por defecto (`aria-hidden`): el texto del botón/enlace que lo
 * acompaña es lo que lee un lector de pantalla.
 */
@Component({
  selector: 'app-icon',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      [attr.width]="size()"
      [attr.height]="size()"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      [attr.stroke-width]="strokeWidth()"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      @for (d of paths(); track $index) {
        <path [attr.d]="d" />
      }
    </svg>
  `,
  styles: [
    `
      :host {
        display: inline-flex;
        line-height: 0;
        flex-shrink: 0;
      }
    `,
  ],
})
export class IconComponent {
  readonly name = input.required<IconName>();
  readonly size = input<number>(20);
  readonly strokeWidth = input<number>(2);

  readonly paths = computed(() => ICONS[this.name()] as readonly string[]);
}
