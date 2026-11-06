package com.cofeapp.backend.platform;

import com.cofeapp.backend.domain.PlatformAdmin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea la cuenta de superadmin al arrancar (V15), con {@code
 * SUPERADMIN_EMAIL} / {@code SUPERADMIN_PASSWORD} del .env.
 *
 * <p>El .env manda: si cambiás la contraseña ahí y reiniciás, la cuenta toma
 * la nueva (y se cierran las sesiones abiertas con la anterior). Sin esas
 * dos variables no se crea nada y el panel no se puede usar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformAdminBootstrap implements ApplicationRunner {

  private static final int MIN_PASSWORD_LENGTH = 10;

  private final PlatformAdminRepository repository;
  private final PasswordEncoder passwordEncoder;
  private final PlatformProperties properties;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    String email = properties.adminEmail() == null ? "" : properties.adminEmail().trim().toLowerCase();
    String password = properties.adminPassword() == null ? "" : properties.adminPassword();
    if (email.isEmpty() || password.isEmpty()) {
      log.info(
          "Panel de superadmin sin configurar: faltan SUPERADMIN_EMAIL / SUPERADMIN_PASSWORD (.env leído: {}).",
          System.getProperty("cofeapp.env-file", "ninguno encontrado junto a las clases"));
      return;
    }
    if (password.length() < MIN_PASSWORD_LENGTH) {
      log.warn("SUPERADMIN_PASSWORD tiene que tener al menos {} caracteres: no se configuró el superadmin.", MIN_PASSWORD_LENGTH);
      return;
    }

    PlatformAdmin admin = repository.findByEmail(email).orElse(null);
    if (admin == null) {
      admin = new PlatformAdmin();
      admin.setEmail(email);
      admin.setFirstName("Superadmin");
      admin.setLastName("COFFE_APP");
      admin.setPasswordHash(passwordEncoder.encode(password));
      repository.save(admin);
      log.info("Cuenta de superadmin creada: {}", email);
    } else if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
      admin.setPasswordHash(passwordEncoder.encode(password));
      admin.setTokenVersion((admin.getTokenVersion() == null ? 0 : admin.getTokenVersion()) + 1);
      admin.setIsActive(true);
      repository.save(admin);
      log.info("Contraseña del superadmin actualizada desde el .env.");
    } else if (!Boolean.TRUE.equals(admin.getIsActive())) {
      admin.setIsActive(true);
      repository.save(admin);
    }

    // La cuenta válida es solo la del .env: si se cambió el email, la
    // anterior deja de funcionar (y sus sesiones se cierran).
    for (PlatformAdmin other : repository.findAll()) {
      if (!other.getId().equals(admin.getId()) && Boolean.TRUE.equals(other.getIsActive())) {
        other.setIsActive(false);
        other.setTokenVersion((other.getTokenVersion() == null ? 0 : other.getTokenVersion()) + 1);
        repository.save(other);
        log.info("Cuenta de superadmin anterior desactivada: {}", other.getEmail());
      }
    }
  }
}
