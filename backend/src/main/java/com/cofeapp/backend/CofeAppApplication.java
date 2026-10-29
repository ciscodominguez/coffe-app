package com.cofeapp.backend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada de la aplicación. El prefijo global {@code /api/v1} y
 * el puerto se configuran en {@code application.yml}
 * ({@code server.servlet.context-path} / {@code server.port}), no acá,
 * para no tener que recompilar por un cambio de configuración.
 */
// Sin UserDetailsServiceAutoConfiguration: el login es propio (AuthService +
// JWT), y ese autoconfig solo servía para imprimir al arrancar un "Using
// generated security password: …" que no se usa para nada y confunde.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class CofeAppApplication {

  public static void main(String[] args) {
    // El .env vive en la carpeta `backend`. Si IntelliJ arranca desde otra
    // carpeta (por ejemplo, la de arriba de todo del proyecto), lo buscamos
    // subiendo desde donde están las clases compiladas (backend/target/classes)
    // y se lo pasamos a application.yml (ver spring.config.import).
    findEnvFile().ifPresent(env -> System.setProperty(ENV_FILE_PROPERTY, env));
    SpringApplication.run(CofeAppApplication.class, args);
  }

  static final String ENV_FILE_PROPERTY = "cofeapp.env-file";

  private static Optional<String> findEnvFile() {
    try {
      Path dir = Paths.get(CofeAppApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      for (int i = 0; i < 4 && dir != null; i++, dir = dir.getParent()) {
        Path env = dir.resolve(".env");
        if (Files.isRegularFile(env)) {
          return Optional.of(env.toAbsolutePath().toString().replace('\\', '/'));
        }
      }
    } catch (Exception ignored) {
      // Sin .env encontrado: quedan las variables de entorno y los valores por defecto.
    }
    return Optional.empty();
  }
}
