package com.cofeapp.backend.config;

import com.cofeapp.backend.common.tenant.TenantFilterInterceptor;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

  private final TenantFilterInterceptor tenantFilterInterceptor;

  @Value("${app.uploads.dir}")
  private String uploadsDir;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    // Orden explícito: tiene que correr DESPUÉS de OpenEntityManagerInViewInterceptor
    // (orden 0), que es el que ata el EntityManager de la request. Ver el
    // Javadoc de TenantFilterInterceptor.
    registry.addInterceptor(tenantFilterInterceptor).order(TenantFilterInterceptor.ORDER);
  }

  /**
   * Sirve como estáticos los archivos guardados por {@link
   * com.cofeapp.backend.media.FileStorageService} (hoy, imágenes de
   * producto — ítem 4 de las mejoras pedidas) bajo {@code /uploads/**}.
   * Spring antepone el {@code context-path} (/api/v1) igual que al resto
   * de la API — ver {@code app.public-base-url} en application.yml, que
   * ya arma la URL pública con ese prefijo incluido.
   *
   * <p>Esta ruta queda pública en {@link SecurityConfig} a propósito: un
   * {@code <img src>} del navegador no puede mandar el header
   * Authorization, así que exigir JWT acá rompería mostrar la imagen.
   */
  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    String location = "file:" + Path.of(uploadsDir).toAbsolutePath().normalize() + "/";
    registry.addResourceHandler("/uploads/**").addResourceLocations(location);
  }
}
