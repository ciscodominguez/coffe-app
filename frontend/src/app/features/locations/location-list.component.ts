import { Component, OnInit, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { LocationResponse, RECEIPT_PAPER_WIDTHS, ReceiptPaperWidth } from '../../core/models/business.model';
import { LocationService } from '../../core/services/business.service';
import { extractErrorMessage } from '../../core/services/error.util';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { ModalComponent } from '../../shared/components/modal/modal.component';

@Component({
    selector: 'app-location-list',
    imports: [ReactiveFormsModule, IconComponent, ModalComponent],
    templateUrl: './location-list.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './location-list.component.css'
})
export class LocationListComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly locationService = inject(LocationService);
  private readonly authService = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly locations = signal<LocationResponse[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly editingId = signal<string | null>(null);
  readonly saving = signal(false);
  readonly paperWidths = RECEIPT_PAPER_WIDTHS;

  /** PATCH /locations/:id exige rol OWNER o ADMIN — ver LocationController del backend. */
  readonly canEdit = computed(() => {
    const role = this.authService.user()?.role;
    return role === 'OWNER' || role === 'ADMIN';
  });

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(150)]],
    address: [''],
    phone: [''],
    receiptPaperWidthMm: [80 as ReceiptPaperWidth, [Validators.required]],
  });

  async ngOnInit(): Promise<void> {
    await this.reload();
  }

  async reload(): Promise<void> {
    this.loading.set(true);
    this.errorMessage.set(null);
    try {
      this.locations.set(await this.locationService.findAll());
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudieron cargar los locales.'));
    } finally {
      this.loading.set(false);
    }
  }

  startEdit(location: LocationResponse): void {
    this.editingId.set(location.id);
    this.form.setValue({
      name: location.name,
      address: location.address ?? '',
      phone: location.phone ?? '',
      receiptPaperWidthMm: location.receiptPaperWidthMm ?? 80,
    });
  }

  /** El local que se está editando (para el título de la ventana). */
  editingLocation(): LocationResponse | null {
    return this.locations().find((location) => location.id === this.editingId()) ?? null;
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  async save(id: string): Promise<void> {
    if (this.form.invalid || this.saving()) return;

    this.saving.set(true);
    try {
      const updated = await this.locationService.update(id, this.form.getRawValue());
      this.locations.set(this.locations().map((location) => (location.id === id ? updated : location)));
      this.editingId.set(null);
      this.toast.success('Local guardado.');
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo guardar el local.'));
    } finally {
      this.saving.set(false);
    }
  }
}
