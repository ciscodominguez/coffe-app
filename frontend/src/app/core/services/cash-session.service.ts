import { Injectable } from '@angular/core';
import {
  CashMovementResponse,
  CashSessionResponse,
  CloseCashSessionInput,
  CreateCashMovementInput,
  OpenCashSessionInput,
} from '../models/cash-session.model';
import { ApiService } from './api.service';

/** Ítem 14 de las mejoras pedidas — ver el Javadoc de `CashSessionController.java` del backend. */
@Injectable({ providedIn: 'root' })
export class CashSessionService {
  constructor(private readonly api: ApiService) {}

  findAllByLocation(locationId: string): Promise<CashSessionResponse[]> {
    return this.api.get<CashSessionResponse[]>('/cash-sessions', { locationId });
  }

  /** El turno abierto de un local — el caller decide qué hacer con un 404 (no hay turno abierto). */
  findCurrent(locationId: string): Promise<CashSessionResponse> {
    return this.api.get<CashSessionResponse>('/cash-sessions/current', { locationId });
  }

  findOne(id: string): Promise<CashSessionResponse> {
    return this.api.get<CashSessionResponse>(`/cash-sessions/${id}`);
  }

  open(input: OpenCashSessionInput): Promise<CashSessionResponse> {
    return this.api.post<CashSessionResponse>('/cash-sessions/open', input);
  }

  addMovement(cashSessionId: string, input: CreateCashMovementInput): Promise<CashMovementResponse> {
    return this.api.post<CashMovementResponse>(`/cash-sessions/${cashSessionId}/movements`, input);
  }

  close(cashSessionId: string, input: CloseCashSessionInput): Promise<CashSessionResponse> {
    return this.api.post<CashSessionResponse>(`/cash-sessions/${cashSessionId}/close`, input);
  }
}
