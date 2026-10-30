import { Injectable } from '@angular/core';
import { ApiService } from './api.service';

/** Espejo de {@code media/dto/ImageUploadResponse.java} del backend. */
export interface ImageUploadResponse {
  url: string;
}

@Injectable({ providedIn: 'root' })
export class MediaService {
  constructor(private readonly api: ApiService) {}

  /**
   * Sube una imagen de producto (multipart/form-data) y devuelve la URL
   * pública ya armada por el backend (ver MediaController/
   * FileStorageService) — el caller la pega en `imageUrl` al crear/editar
   * el producto. Reusa {@link ApiService#post}: Angular `HttpClient`
   * detecta un body `FormData` solo y arma el `Content-Type`
   * multipart/boundary correcto sin que haga falta nada especial acá.
   */
  uploadProductImage(file: File): Promise<ImageUploadResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.api.post<ImageUploadResponse>('/media/products/image', formData);
  }
}
