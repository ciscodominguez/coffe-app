package com.cofeapp.backend.media;

import com.cofeapp.backend.media.dto.ImageUploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Subida de archivos — hoy solo imágenes de producto (ítem 4 de las
 * mejoras pedidas). Devuelve la URL pública ya armada; el frontend la
 * pega en {@code imageUrl} al crear/editar el producto (ver {@code
 * ProductController}) — subir el archivo y guardar el producto quedan
 * como dos pasos separados a propósito, así el mismo endpoint sirve
 * tanto para alta como para edición sin un flujo especial.
 *
 * <p>Mismo criterio de roles que {@code ProductController}: solo
 * OWNER/ADMIN pueden cargar catálogo.
 */
@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class MediaController {

  private final FileStorageService fileStorageService;

  @PostMapping("/products/image")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public ImageUploadResponse uploadProductImage(@RequestParam("file") MultipartFile file) {
    return new ImageUploadResponse(fileStorageService.storeProductImage(file));
  }
}
