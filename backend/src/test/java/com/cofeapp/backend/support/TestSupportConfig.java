package com.cofeapp.backend.support;

import java.sql.Connection;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Configuración que solo existe en los tests (la importa {@link IntegrationTest}).
 *
 * <ul>
 *   <li>Arranca siempre con la base de test vacía: Flyway la borra y corre
 *       todas las migraciones (V1…V10). Así también se prueba que las
 *       migraciones arman el esquema que Hibernate espera.
 *   <li>Reemplaza Mercado Pago por {@link FakeMercadoPagoClient} (V10).
 *   <li>Reemplaza el envío de mails por {@link RecordingMailService}: los
 *       tests leen el link de "Olvidé mi contraseña" de ahí y nunca sale un
 *       mail de verdad, aunque el .env tenga un servidor de correo.
 * </ul>
 */
@TestConfiguration
public class TestSupportConfig {

  @Bean
  FlywayMigrationStrategy cleanTestDatabaseThenMigrate() {
    return flyway -> {
      assertIsTestDatabase(flyway);
      flyway.clean();
      flyway.migrate();
    };
  }

  /** Mercado Pago simulado (V10): los tests nunca llaman a la API real. */
  @Bean
  @Primary
  FakeMercadoPagoClient fakeMercadoPagoClient() {
    return new FakeMercadoPagoClient();
  }

  @Bean
  @Primary
  RecordingMailService recordingMailService(ObjectProvider<JavaMailSender> mailSender) {
    return new RecordingMailService(mailSender);
  }

  /**
   * Freno de seguridad: {@code clean()} borra TODAS las tablas. Si por un
   * error de configuración los tests apuntaran a la base real del café, esto
   * corta antes de tocar nada.
   */
  private static void assertIsTestDatabase(Flyway flyway) {
    try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
      String database = connection.getCatalog();
      if (database == null || !database.toLowerCase().contains("test")) {
        throw new IllegalStateException(
            "Los tests borran la base al arrancar y están apuntando a '"
                + database
                + "', que no parece una base de test. Usá una base cuyo nombre tenga \"test\" "
                + "(por defecto cofe_app_test — ver application-test.yml).");
      }
    } catch (SQLException e) {
      throw new IllegalStateException(
          "No se pudo conectar a la base de test. ¿Creaste cofe_app_test y le diste permisos al usuario del .env? "
              + "(ver README, sección \"Tests del backend\")",
          e);
    }
  }
}
