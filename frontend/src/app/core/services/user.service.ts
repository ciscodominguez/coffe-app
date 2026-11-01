import { Injectable } from '@angular/core';
import { CreateUserInput, UpdateUserInput, UserResponse } from '../models/user.model';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class UserService {
  constructor(private readonly api: ApiService) {}

  findAll(includeInactive = false): Promise<UserResponse[]> {
    return this.api.get<UserResponse[]>('/users', { includeInactive });
  }

  create(input: CreateUserInput): Promise<UserResponse> {
    return this.api.post<UserResponse>('/users', input);
  }

  update(id: string, input: UpdateUserInput): Promise<UserResponse> {
    return this.api.patch<UserResponse>(`/users/${id}`, input);
  }

  deactivate(id: string): Promise<UserResponse> {
    return this.api.delete<UserResponse>(`/users/${id}`);
  }
}
