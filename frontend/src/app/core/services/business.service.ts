import { Injectable } from '@angular/core';
import {
  BusinessDisplayNameResponse,
  BusinessResponse,
  LocationResponse,
  UpdateBusinessInput,
  UpdateLocationInput,
} from '../models/business.model';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class BusinessService {
  constructor(private readonly api: ApiService) {}

  getMe(): Promise<BusinessResponse> {
    return this.api.get<BusinessResponse>('/businesses/me');
  }

  /** Abierto a cualquier rol — lo usa el reporte de Caja (WAITER incluido). getMe() es solo de gestión. */
  getDisplayName(): Promise<BusinessDisplayNameResponse> {
    return this.api.get<BusinessDisplayNameResponse>('/businesses/me/display-name');
  }

  updateMe(input: UpdateBusinessInput): Promise<BusinessResponse> {
    return this.api.patch<BusinessResponse>('/businesses/me', input);
  }
}

@Injectable({ providedIn: 'root' })
export class LocationService {
  constructor(private readonly api: ApiService) {}

  findAll(): Promise<LocationResponse[]> {
    return this.api.get<LocationResponse[]>('/locations');
  }

  update(id: string, input: UpdateLocationInput): Promise<LocationResponse> {
    return this.api.patch<LocationResponse>(`/locations/${id}`, input);
  }
}
