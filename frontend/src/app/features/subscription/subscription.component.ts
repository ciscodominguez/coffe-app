import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { extractErrorMessage } from '../../core/services/error.util';
import { SubscriptionService, SubscriptionStatus } from '../../core/services/subscription.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent, IconName } from '../../shared/components/icon/icon.component';
import { ModalComponent } from '../../shared/components/modal/modal.component';

interface Headline {
  tone: 'success' | 'info' | 'warning' | 'danger';
  icon: IconName;
  title: string;
  text: string;
}

/**
 * "Suscripción" (paso 3.2): el abono mensual de COFFE_APP. El dueño se
 * suscribe con débito automático de Mercado Pago o lo cancela; el
 * administrador solo lo ve. Si el café está suspendido por falta de pago,
 * es la única pantalla que puede abrir el dueño.
 */
@Component({
    selector: 'app-subscription',
    imports: [DatePipe, DecimalPipe, FormsModule, IconComponent, ModalComponent],
    templateUrl: './subscription.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './subscription.component.css'
})
export class SubscriptionComponent {
  private readonly service = inject(SubscriptionService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly status = signal<SubscriptionStatus | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly working = signal(false);
  readonly formError = signal<string | null>(null);
  readonly confirmCancel = signal(false);
  readonly payerEmail = signal(this.auth.user()?.email ?? '');
  /** Volvió de la página de Mercado Pago. */
  readonly returned = signal(inject(ActivatedRoute).snapshot.queryParamMap.get('volviste') === '1');

  readonly isOwner = computed(() => this.auth.user()?.role === 'OWNER');

  readonly headline = computed<Headline | null>(() => {
    const s = this.status();
    if (!s) return null;
    const until = s.accessUntil ? fmt(s.accessUntil) : '';
    const days = s.daysLeft === 1 ? '1 día' : `${s.daysLeft} días`;
    switch (s.state) {
      case 'TRIAL':
        return {
          tone: 'info',
          icon: 'sparkles',
          title: 'Estás en la prueba gratis',
          text:
            s.subscriptionStatus === 'authorized'
              ? `Te quedan ${days}. Ya te suscribiste: el primer débito es el ${until}.`
              : `Te quedan ${days} (hasta el ${until}). Suscribite antes para seguir usándolo sin cortes.`,
        };
      case 'ACTIVE':
        return {
          tone: 'success',
          icon: 'check',
          title: 'Suscripción al día',
          text:
            s.subscriptionStatus === 'authorized'
              ? `Está pago hasta el ${until}. Ese día se debita solo el mes siguiente.`
              : `Está pago hasta el ${until}. No tenés débito automático: suscribite para no tener que acordarte.`,
        };
      case 'PAST_DUE':
        return {
          tone: 'warning',
          icon: 'alert',
          title: s.trialEndsAt && s.accessUntil === s.trialEndsAt ? 'Se terminó la prueba gratis' : 'No registramos el pago',
          text:
            `Tenés ${days} (hasta el ${s.suspendsAt ? fmt(s.suspendsAt) : ''}) para pagar. Después el café se suspende y nadie del equipo puede entrar.` +
            (s.subscriptionStatus === 'authorized' ? ' Mercado Pago reintenta el débito solo; si la tarjeta no tiene fondos o venció, suscribite de nuevo con otra.' : ''),
        };
      case 'SUSPENDED':
        return {
          tone: 'danger',
          icon: 'lock',
          title: 'El café está suspendido por falta de pago',
          text:
            'Nadie del equipo puede entrar. Pagá la suscripción y se reactiva solo apenas se acredita (tus datos están intactos).' +
            (s.subscriptionStatus === 'authorized' ? ' Si te rechazaron la tarjeta, suscribite de nuevo con otra.' : ''),
        };
      case 'EXEMPT':
        return { tone: 'success', icon: 'check', title: 'Tu café no paga suscripción', text: 'Lo tenés sin cargo.' };
      default:
        return null;
    }
  });

  /** Tiene un débito automático vivo (se puede cancelar). */
  readonly hasSubscription = computed(() => {
    const st = this.status()?.subscriptionStatus;
    return st === 'authorized' || st === 'paused' || st === 'pending';
  });

  constructor() {
    void this.load();
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.errorMessage.set(null);
    try {
      this.status.set(this.returned() ? await this.service.refresh() : await this.service.load());
      const payer = this.status()?.payerEmail;
      if (payer) this.payerEmail.set(payer);
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudo cargar la suscripción.'));
    } finally {
      this.loading.set(false);
    }
  }

  async refresh(): Promise<void> {
    this.working.set(true);
    try {
      this.status.set(await this.service.refresh());
      this.toast.success('Actualizado.');
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo actualizar.'));
    } finally {
      this.working.set(false);
    }
  }

  async subscribe(): Promise<void> {
    const email = this.payerEmail().trim();
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
      this.formError.set('Escribí el email de tu cuenta de Mercado Pago.');
      return;
    }
    this.working.set(true);
    this.formError.set(null);
    try {
      const url = await this.service.checkout(email);
      window.location.href = url; // página de Mercado Pago; vuelve a /suscripcion?volviste=1
    } catch (error) {
      this.formError.set(extractErrorMessage(error, 'No se pudo abrir Mercado Pago.'));
      this.working.set(false);
    }
  }

  async cancel(): Promise<void> {
    this.working.set(true);
    try {
      this.status.set(await this.service.cancel());
      this.confirmCancel.set(false);
      this.toast.success('Listo: se canceló el débito automático.');
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo cancelar.'));
    } finally {
      this.working.set(false);
    }
  }

  subscriptionLabel(s: SubscriptionStatus): string {
    switch (s.subscriptionStatus) {
      case 'authorized':
        return `Débito automático activo${s.payerEmail ? ' (' + s.payerEmail + ')' : ''}`;
      case 'pending':
        return 'Empezaste a suscribirte pero falta confirmarlo en Mercado Pago';
      case 'paused':
        return 'Débito automático en pausa';
      case 'cancelled':
        return 'Débito automático cancelado';
      default:
        return 'Sin débito automático';
    }
  }
}

function fmt(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString('es-AR', { day: 'numeric', month: 'long' });
}
