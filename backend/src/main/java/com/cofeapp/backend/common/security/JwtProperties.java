package com.cofeapp.backend.common.security;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuración del JWT de sesión: {@code JWT_SECRET} y
 * {@code JWT_EXPIRES_IN}.
 *
 * <p>{@code expiration} se bindea directo a un {@link Duration} — Spring
 * Boot entiende el formato simple ("8h", "60m", "30s"), así que
 * {@code JWT_EXPIRES_IN=8h} en el archivo {@code .env} funciona tal cual.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

  /** Mínimo 32 caracteres. */
  private String secret;

  private Duration expiration = Duration.ofHours(8);
}
