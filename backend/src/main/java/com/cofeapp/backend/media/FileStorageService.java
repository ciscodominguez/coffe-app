package com.cofeapp.backend.media;

import com.cofeapp.backend.common.exception.BadRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Guarda archivos subidos (hoy, solo imágenes de producto — ítem 4 de las
 * mejoras pedidas) en disco local, bajo {@code app.uploads.dir}, y
 * devuelve la URL pública con la que después se sirven — ver {@link
 * com.cofeapp.backend.config.WebMvcConfig} (mapea {@code /uploads/**} a
 * esa misma carpeta) y {@link com.cofeapp.backend.config.SecurityConfig}
 * (esa ruta queda pública).
 *
 * <p>Simplificación deliberada de esta ronda: disco local del servidor,
 * no un bucket (S3/GCS/etc) — alcanza para el despliegue actual (un
 * servidor por negocio). Si en una etapa futura hace falta escalar a
 * varias instancias del backend, este es el único lugar que habría que
 * tocar (cambiar {@link #storeProductImage} por un cliente de storage
 * externo, sin tocar {@code MediaController} ni el resto del catálogo).
 */
@Slf4j
@Service
public class FileStorageService {

  private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
  private static final long MAX_BYTES = 5L * 1024 * 1024; // 5 MB — ver también spring.servlet.multipart.max-file-size.

  private final Path productsDir;
  private final String publicBaseUrl;

  public FileStorageService(
      @Value("${app.uploads.dir}") String uploadsDir, @Value("${app.public-base-url}") String publicBaseUrl) {
    this.productsDir = Path.of(uploadsDir, "products").toAbsolutePath().normalize();
    this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    try {
      Files.createDirectories(productsDir);
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo crear el directorio de uploads: " + productsDir, e);
    }
  }

  /** Valida, guarda la imagen de un producto con nombre generado (nunca el original) y devuelve su URL pública. */
  public String storeProductImage(MultipartFile file) {
    validate(file);

    String filename = UUID.randomUUID() + extensionFor(file.getContentType());
    Path destination = productsDir.resolve(filename);

    try (InputStream in = file.getInputStream()) {
      Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      log.error("No se pudo guardar la imagen subida", e);
      throw new IllegalStateException("No se pudo guardar la imagen.", e);
    }

    return publicBaseUrl + "/uploads/products/" + filename;
  }

  private void validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BadRequestException("No se recibió ningún archivo.");
    }
    if (file.getSize() > MAX_BYTES) {
      throw new BadRequestException("La imagen no puede superar los 5 MB.");
    }
    String contentType = file.getContentType();
    if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
      throw new BadRequestException("Formato de imagen no soportado — usá JPG, PNG o WEBP.");
    }
  }

  private String extensionFor(String contentType) {
    return switch (contentType) {
      case "image/png" -> ".png";
      case "image/webp" -> ".webp";
      default -> ".jpg";
    };
  }
}
