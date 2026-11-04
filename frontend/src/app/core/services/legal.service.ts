import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';

export interface LegalStatus {
  currentVersion: string;
  acceptedVersion: string | null;
  acceptedAt: string | null;
  /** El dueño tiene que aceptar la versión vigente (nunca la aceptó, o hay una nueva). */
  mustAccept: boolean;
}

/** Aceptación de los Términos y la Política de privacidad (V17). */
@Injectable({ providedIn: 'root' })
export class LegalService {
  private readonly api = inject(ApiService);

  status(): Promise<LegalStatus> {
    return this.api.get('/legal/status');
  }

  accept(version: string): Promise<LegalStatus> {
    return this.api.post('/legal/accept', { version });
  }
}
