import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { extractErrorMessage } from '../../core/services/error.util';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { LegalLinksComponent } from './legal-links.component';

type RequestType = 'WITHDRAWAL' | 'CANCELLATION';

/**
 * "Botón de arrepentimiento" y "Botón de baja" (paso 3.3 — Defensa del
 * Consumidor). Público: se usa sin tener que entrar. Da el código de
 * trámite al instante.
 */
@Component({
    selector: 'app-service-request',
    imports: [FormsModule, RouterLink, IconComponent, LegalLinksComponent],
    templateUrl: './service-request.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrls: ['./legal-page.css', './service-request.component.css']
})
export class ServiceRequestComponent {
  private readonly api = inject(ApiService);

  readonly type = signal<RequestType>(inject(ActivatedRoute).snapshot.data['type'] ?? 'CANCELLATION');
  readonly isWithdrawal = computed(() => this.type() === 'WITHDRAWAL');

  readonly contactName = signal('');
  readonly email = signal('');
  readonly taxId = signal('');
  readonly cafeName = signal('');
  readonly message = signal('');

  readonly sending = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly result = signal<{ code: string; createdAt: string } | null>(null);

  async submit(): Promise<void> {
    if (!this.contactName().trim()) return this.errorMessage.set('Escribí tu nombre.');
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(this.email().trim())) return this.errorMessage.set('Escribí un email válido.');
    const digits = this.taxId().replace(/\D/g, '');
    if (digits && digits.length !== 11) return this.errorMessage.set('El CUIT tiene 11 números.');

    this.sending.set(true);
    this.errorMessage.set(null);
    try {
      this.result.set(
        await this.api.post<{ code: string; createdAt: string }>('/legal/requests', {
          type: this.type(),
          contactName: this.contactName().trim(),
          email: this.email().trim(),
          taxId: digits || null,
          cafeName: this.cafeName().trim() || null,
          message: this.message().trim() || null,
        }),
      );
      window.scrollTo({ top: 0 });
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudo mandar el pedido. Probá de nuevo.'));
    } finally {
      this.sending.set(false);
    }
  }
}
