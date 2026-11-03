package com.cofeapp.backend.auth;

import com.cofeapp.backend.common.exception.TooManyRequestsException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Freno contra adivinar contraseñas probando muchas veces (V9, "cuentas: lo
 * básico"): después de {@link #MAX_FAILURES} intentos fallidos seguidos para
 * el mismo email, el login de ese email queda bloqueado {@link #LOCK} — aunque
 * después se ponga la contraseña correcta. Un login bueno limpia el contador.
 *
 * <p>Se lleva en memoria (se reinicia si se reinicia el backend) a propósito:
 * alcanza para un solo servidor, que es el escenario de hoy. Si algún día hay
 * varios servidores detrás de un balanceador, esto pasa a una tabla o a Redis.
 *
 * <p>La misma clase frena los pedidos de "Olvidé mi contraseña" (máximo
 * {@link #MAX_RESET_REQUESTS} por email por hora) para que nadie pueda
 * llenarle la casilla de mails a otra persona.
 */
@Component
public class LoginAttemptService {

  static final int MAX_FAILURES = 5;
  static final Duration LOCK = Duration.ofMinutes(15);
  static final int MAX_RESET_REQUESTS = 3;
  static final Duration RESET_WINDOW = Duration.ofHours(1);

  private record Counter(int count, Instant since, Instant lockedUntil) {}

  private final Map<String, Counter> failures = new ConcurrentHashMap<>();
  private final Map<String, Counter> resetRequests = new ConcurrentHashMap<>();

  /** Llamar ANTES de verificar la contraseña: si está bloqueado, corta ahí. */
  public void assertNotLocked(String email) {
    Counter counter = failures.get(email);
    if (counter != null && counter.lockedUntil() != null && Instant.now().isBefore(counter.lockedUntil())) {
      long minutes = Math.max(1, Duration.between(Instant.now(), counter.lockedUntil()).toMinutes() + 1);
      throw new TooManyRequestsException(
          "Demasiados intentos fallidos. Probá de nuevo en " + minutes + " minuto" + (minutes == 1 ? "" : "s") + ".");
    }
  }

  public void loginFailed(String email) {
    Instant now = Instant.now();
    failures.compute(
        email,
        (key, current) -> {
          // Si el bloqueo anterior ya venció, se arranca de cero.
          if (current == null || (current.lockedUntil() != null && now.isAfter(current.lockedUntil()))) {
            return new Counter(1, now, null);
          }
          int count = current.count() + 1;
          return new Counter(count, current.since(), count >= MAX_FAILURES ? now.plus(LOCK) : null);
        });
  }

  public void loginSucceeded(String email) {
    failures.remove(email);
  }

  /** "Olvidé mi contraseña": máximo {@link #MAX_RESET_REQUESTS} por email por hora. */
  public void registerResetRequest(String email) {
    Instant now = Instant.now();
    Counter counter =
        resetRequests.compute(
            email,
            (key, current) -> {
              if (current == null || now.isAfter(current.since().plus(RESET_WINDOW))) {
                return new Counter(1, now, null);
              }
              return new Counter(current.count() + 1, current.since(), null);
            });
    if (counter.count() > MAX_RESET_REQUESTS) {
      throw new TooManyRequestsException("Ya pediste varios links. Revisá tu correo o probá de nuevo en un rato.");
    }
  }
}
