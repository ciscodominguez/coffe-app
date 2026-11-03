package com.cofeapp.backend.auth;

import com.cofeapp.backend.common.exception.BadRequestException;
import com.cofeapp.backend.common.mail.MailService;
import com.cofeapp.backend.domain.PasswordResetToken;
import com.cofeapp.backend.domain.User;
import com.cofeapp.backend.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Olvidé mi contraseña" (V9):
 *
 * <ol>
 *   <li>{@link #requestReset}: si el email es de un usuario activo, genera un
 *       token al azar, guarda SOLO su hash (SHA-256) con vencimiento de
 *       {@link #TOKEN_TTL}, y manda el link por mail. Siempre responde lo
 *       mismo exista o no el email — así nadie puede usar este formulario
 *       para averiguar qué emails están registrados.
 *   <li>{@link #resetPassword}: con el token del link, cambia la contraseña,
 *       marca el link como usado (sirve una sola vez) y cierra todas las
 *       sesiones abiertas de esa persona ({@code User#revokeSessions}).
 * </ol>
 *
 * <p>Corre sin sesión (igual que el login), así que busca al usuario "crudo"
 * por email/id: todavía no hay negocio en el contexto.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

  static final Duration TOKEN_TTL = Duration.ofHours(1);

  private final UserRepository userRepository;
  private final PasswordResetTokenRepository tokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final MailService mailService;
  private final LoginAttemptService loginAttemptService;
  private final SecureRandom random = new SecureRandom();

  /** Dirección del frontend, para armar el link del mail. */
  @Value("${app.frontend-url:http://localhost:4200}")
  private String frontendUrl;

  @Transactional
  public void requestReset(String rawEmail) {
    String email = rawEmail.trim().toLowerCase();
    loginAttemptService.registerResetRequest(email);

    User user = userRepository.findByEmail(email).orElse(null);
    if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
      return; // Misma respuesta que si existiera — ver el Javadoc de la clase.
    }

    tokenRepository.deleteAllByUserId(user.getId());

    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

    PasswordResetToken entity = new PasswordResetToken();
    entity.setUserId(user.getId());
    entity.setTokenHash(sha256(token));
    entity.setExpiresAt(Instant.now().plus(TOKEN_TTL));
    tokenRepository.save(entity);

    String link = frontendUrl.replaceAll("/+$", "") + "/restablecer?token=" + token;
    mailService.send(
        user.getEmail(),
        "Restablecer tu contraseña de COFFE_APP",
        "Hola " + user.getFirstName() + ",\n\n"
            + "Alguien (ojalá vos) pidió restablecer la contraseña de tu cuenta de COFFE_APP.\n\n"
            + "Para elegir una contraseña nueva, entrá a este link (vence en una hora y sirve una sola vez):\n\n"
            + link + "\n\n"
            + "Si no fuiste vos, ignorá este mail: tu contraseña sigue siendo la misma.\n");
  }

  @Transactional
  public void resetPassword(String token, String newPassword) {
    PasswordResetToken entity =
        tokenRepository
            .findByTokenHash(sha256(token))
            .orElseThrow(() -> new BadRequestException("El link no es válido. Pedí uno nuevo."));
    if (entity.getUsedAt() != null) {
      throw new BadRequestException("Ese link ya se usó. Si necesitás, pedí uno nuevo.");
    }
    if (Instant.now().isAfter(entity.getExpiresAt())) {
      throw new BadRequestException("El link venció. Pedí uno nuevo.");
    }

    User user =
        userRepository
            .findByIdScoped(entity.getUserId())
            .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
            .orElseThrow(() -> new BadRequestException("El link no es válido. Pedí uno nuevo."));

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    user.revokeSessions();
    userRepository.save(user);

    entity.setUsedAt(Instant.now());
    tokenRepository.save(entity);
    loginAttemptService.loginSucceeded(user.getEmail()); // Si estaba bloqueado por intentos, se destraba.
  }

  private static String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 no disponible", e);
    }
  }
}
