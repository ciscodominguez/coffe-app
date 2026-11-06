import { Component, OnInit, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  KNOWN_CHANNELS,
  SalesChannel,
  SalesChannelKind,
  SalesChannelMode,
  channelTone,
} from '../../core/models/sales-channel.model';
import { extractErrorMessage } from '../../core/services/error.util';
import { LocationResolverService } from '../../core/services/location-resolver.service';
import { SalesChannelService } from '../../core/services/sales-channel.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

interface ChannelCard {
  kind: SalesChannelKind;
  name: string;
  hint: string;
  channel: SalesChannel | null;
}

/**
 * Apps de delivery (V19, Fase A de docs/INTEGRACION-APPS-DELIVERY.md).
 * El dueño o el administrador prende solo las apps que usa el local. Todo
 * arranca apagado: un café sin apps no ve nada nuevo en ninguna pantalla.
 */
@Component({
    selector: 'app-delivery-apps',
    imports: [FormsModule, RouterLink, IconComponent],
    templateUrl: './delivery-apps.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './delivery-apps.component.css'
})
export class DeliveryAppsComponent implements OnInit {
  private readonly service = inject(SalesChannelService);
  private readonly locationResolver = inject(LocationResolverService);
  private readonly toast = inject(ToastService);

  readonly tone = channelTone;
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly channels = signal<SalesChannel[]>([]);
  readonly busy = signal<string | null>(null);
  private locationId: string | null = null;

  readonly newOtherName = signal('');
  readonly addingOther = signal(false);

  readonly cards = computed<ChannelCard[]>(() =>
    KNOWN_CHANNELS.map((k) => ({
      kind: k.kind,
      name: k.name,
      hint: k.hint,
      channel: this.channels().find((c) => c.kind === k.kind) ?? null,
    })),
  );
  readonly others = computed(() => this.channels().filter((c) => c.kind === 'OTHER'));
  readonly onCount = computed(() => this.channels().filter((c) => c.mode !== 'OFF').length);

  async ngOnInit(): Promise<void> {
    try {
      this.locationId = await this.locationResolver.resolve();
      this.channels.set(await this.service.findAll(this.locationId));
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudieron cargar las apps.'));
    } finally {
      this.loading.set(false);
    }
  }

  /** Prende/apaga una app conocida (la crea la primera vez que se prende). */
  async setMode(card: ChannelCard, mode: SalesChannelMode): Promise<void> {
    if (mode === 'AUTO' || this.busy()) return;
    if (!card.channel && mode === 'OFF') return;
    await this.run(card.kind, async () => {
      const saved = card.channel
        ? await this.service.update(card.channel.id, { mode })
        : await this.service.create({ locationId: this.locationId!, kind: card.kind, mode, commissionPct: 0 });
      this.replace(saved);
      this.toast.success(mode === 'OFF' ? `${saved.name} apagada.` : `${saved.name} prendida en modo manual.`);
    });
  }

  async setOtherMode(channel: SalesChannel, mode: SalesChannelMode): Promise<void> {
    if (mode === 'AUTO' || this.busy() || channel.mode === mode) return;
    await this.run(channel.id, async () => {
      this.replace(await this.service.update(channel.id, { mode }));
    });
  }

  async saveCommission(channel: SalesChannel, raw: string): Promise<void> {
    const value = Number(String(raw).replace(',', '.'));
    if (raw === '' || Number.isNaN(value) || value < 0 || value > 100) {
      this.toast.error('La comisión tiene que ser un número entre 0 y 100.');
      return;
    }
    if (value === Number(channel.commissionPct)) return;
    await this.run(channel.id, async () => {
      this.replace(await this.service.update(channel.id, { commissionPct: value }));
      this.toast.success(`Comisión de ${channel.name}: ${value}%.`);
    });
  }

  async addOther(): Promise<void> {
    const name = this.newOtherName().trim();
    if (!name) {
      this.toast.error('Escribí el nombre de la app.');
      return;
    }
    await this.run('new-other', async () => {
      const saved = await this.service.create({ locationId: this.locationId!, kind: 'OTHER', name, mode: 'MANUAL', commissionPct: 0 });
      this.channels.set([...this.channels(), saved]);
      this.newOtherName.set('');
      this.addingOther.set(false);
      this.toast.success(`${saved.name} agregada.`);
    });
  }

  async rename(channel: SalesChannel, raw: string): Promise<void> {
    const name = raw.trim();
    if (!name || name === channel.name) return;
    await this.run(channel.id, async () => {
      this.replace(await this.service.update(channel.id, { name }));
    });
  }

  private replace(saved: SalesChannel): void {
    const list = this.channels();
    this.channels.set(list.some((c) => c.id === saved.id) ? list.map((c) => (c.id === saved.id ? saved : c)) : [...list, saved]);
  }

  private async run(key: string, action: () => Promise<void>): Promise<void> {
    this.busy.set(key);
    try {
      await action();
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo guardar.'));
    } finally {
      this.busy.set(null);
    }
  }
}
