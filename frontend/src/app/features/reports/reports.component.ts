import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LocationResponse } from '../../core/models/business.model';
import { LocationService } from '../../core/services/business.service';
import { extractErrorMessage } from '../../core/services/error.util';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent, IconName } from '../../shared/components/icon/icon.component';
import { ReportService } from './report.service';

type Preset = 'today' | 'yesterday' | 'week' | 'month' | 'lastMonth' | 'custom';

/** "2026-09-28" en la fecha local (no en UTC). */
function isoDay(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function parseDay(value: string): Date {
  const [y, m, d] = value.split('-').map(Number);
  return new Date(y, m - 1, d);
}

/**
 * Pantalla "Reportes" (paso 2.6): elegís el período y el local, y bajás un
 * Excel con Resumen, Ventas, Productos, Cierres de caja y Descuentos, para
 * mandarle al contador.
 */
@Component({
    selector: 'app-reports',
    imports: [CommonModule, FormsModule, IconComponent],
    templateUrl: './reports.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './reports.component.css'
})
export class ReportsComponent implements OnInit {
  private readonly reportService = inject(ReportService);
  private readonly locationService = inject(LocationService);
  private readonly toast = inject(ToastService);

  readonly presets: readonly { key: Preset; label: string }[] = [
    { key: 'today', label: 'Hoy' },
    { key: 'yesterday', label: 'Ayer' },
    { key: 'week', label: 'Esta semana' },
    { key: 'month', label: 'Este mes' },
    { key: 'lastMonth', label: 'Mes pasado' },
    { key: 'custom', label: 'Otras fechas' },
  ];

  readonly sheets: readonly { icon: IconName; title: string; text: string }[] = [
    { icon: 'chart', title: 'Resumen', text: 'Ventas, pedidos, ticket promedio, descuentos y lo cobrado con cada método.' },
    { icon: 'receipt', title: 'Ventas', text: 'Un renglón por pedido entregado, con lo cobrado en efectivo, débito, QR…' },
    { icon: 'coffee', title: 'Productos', text: 'Unidades y plata de cada producto.' },
    { icon: 'wallet', title: 'Cierres de caja', text: 'Cada turno: apertura, ventas, esperado, contado y diferencia.' },
    { icon: 'percent', title: 'Descuentos', text: 'Cada descuento aplicado, quién lo puso y por qué.' },
  ];

  readonly locations = signal<LocationResponse[]>([]);
  readonly locationId = signal<string>('');
  readonly preset = signal<Preset>('month');
  readonly fromDay = signal(isoDay(new Date()));
  readonly toDay = signal(isoDay(new Date()));
  readonly downloading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  /** El período elegido: [desde, hasta] como días locales. */
  readonly range = computed<{ from: Date; to: Date }>(() => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    switch (this.preset()) {
      case 'today':
        return { from: today, to: today };
      case 'yesterday': {
        const y = new Date(today);
        y.setDate(y.getDate() - 1);
        return { from: y, to: y };
      }
      case 'week': {
        const monday = new Date(today);
        monday.setDate(monday.getDate() - ((monday.getDay() + 6) % 7));
        return { from: monday, to: today };
      }
      case 'month':
        return { from: new Date(today.getFullYear(), today.getMonth(), 1), to: today };
      case 'lastMonth':
        return {
          from: new Date(today.getFullYear(), today.getMonth() - 1, 1),
          to: new Date(today.getFullYear(), today.getMonth(), 0),
        };
      case 'custom':
        return { from: parseDay(this.fromDay()), to: parseDay(this.toDay()) };
    }
  });

  readonly rangeValid = computed(() => this.range().from.getTime() <= this.range().to.getTime());

  async ngOnInit(): Promise<void> {
    try {
      const locations = (await this.locationService.findAll()).filter((l) => l.isActive);
      this.locations.set(locations);
    } catch {
      // Sin la lista, se descarga igual con "Todos los locales".
    }
  }

  choosePreset(preset: Preset): void {
    if (preset === 'custom' && this.preset() !== 'custom') {
      this.fromDay.set(isoDay(this.range().from));
      this.toDay.set(isoDay(this.range().to));
    }
    this.preset.set(preset);
  }

  async download(): Promise<void> {
    if (this.downloading() || !this.rangeValid()) return;
    const { from, to } = this.range();
    const end = new Date(to);
    end.setDate(end.getDate() + 1); // hasta el final del último día
    this.downloading.set(true);
    this.errorMessage.set(null);
    try {
      const blob = await this.reportService.excel(from, end, this.locationId() || null);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `reporte-${isoDay(from)}-al-${isoDay(to)}.xlsx`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 10_000);
      this.toast.success('Listo: el Excel se descargó.');
    } catch (error) {
      this.errorMessage.set(await this.readError(error));
    } finally {
      this.downloading.set(false);
    }
  }

  /** Los errores de una descarga llegan como archivo: se lee el mensaje de adentro. */
  private async readError(error: unknown): Promise<string> {
    if (error instanceof HttpErrorResponse && error.error instanceof Blob) {
      try {
        const body = JSON.parse(await error.error.text()) as { message?: string };
        if (body.message) return body.message;
      } catch {
        // cae al mensaje genérico
      }
    }
    return extractErrorMessage(error, 'No se pudo armar el reporte.');
  }
}
