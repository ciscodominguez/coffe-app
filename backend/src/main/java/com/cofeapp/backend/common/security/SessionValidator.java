package com.cofeapp.backend.common.security;

import com.cofeapp.backend.business.BusinessRepository;
import com.cofeapp.backend.domain.Business;
import com.cofeapp.backend.domain.PlatformAdmin;
import com.cofeapp.backend.domain.User;
import com.cofeapp.backend.domain.enums.BusinessStatus;
import com.cofeapp.backend.platform.PlatformAdminRepository;
import com.cofeapp.backend.platform.PlatformProperties;
import com.cofeapp.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Chequea que un JWT válido (firma y vencimiento OK) siga correspondiendo a
 * una sesión VIGENTE: que el usuario exista, esté activo, sea del negocio
 * que dice el token y que su {@code tokenVersion} no haya cambiado desde que
 * se emitió (V9). Es lo que hace que dar de baja a alguien o cambiarle la
 * contraseña lo saque de la app al instante, en vez de esperar a que se le
 * venza el token.
 *
 * <p>Una consulta chica por request (por id, clave primaria). Corre en
 * {@link JwtAuthenticationFilter} — antes de que {@code
 * TenantFilterInterceptor} habilite el filtro de Hibernate — así que busca
 * "crudo" por id y compara el negocio a mano; y en el handshake del
 * WebSocket ({@link WebSocketAuthHandshakeInterceptor}).
 */
@Component
@RequiredArgsConstructor
public class SessionValidator {

  private final UserRepository userRepository;
  private final BusinessRepository businessRepository;
  private final PlatformAdminRepository platformAdminRepository;
  private final PlatformProperties platformProperties;

  public boolean isCurrent(JwtService.ParsedToken token) {
    return rejection(token) == null;
  }

  /**
   * Resultado de revisar una sesión: {@code message} nulo = vale.
   * {@code paymentOnly} (V16): el café está suspendido por falta de pago y
   * es el dueño — puede entrar, pero solo a pagar la suscripción.
   */
  public record Verdict(String message, boolean paymentOnly) {
    static final Verdict OK = new Verdict(null, false);

    public boolean ok() {
      return message == null;
    }
  }

  public Verdict check(JwtService.ParsedToken token) {
    String message = rejection(token);
    if (message == null) return Verdict.OK;
    if (!token.platform() && "OWNER".equals(token.user().role())) {
      Business business = businessRepository.findById(token.user().businessId()).orElse(null);
      if (business != null && business.isBlockedForPayment() && message.equals(blockedMessage(business, platformProperties))) {
        return new Verdict(PAYMENT_ONLY_MESSAGE, true);
      }
    }
    return new Verdict(message, false);
  }

  public static final String PAYMENT_ONLY_MESSAGE =
      "El café está suspendido por falta de pago. Pagá la suscripción para volver a usarlo.";

  /**
   * {@code null} si la sesión sigue valiendo; si no, el motivo para mostrar.
   * V15: si el café está suspendido o dado de baja, la sesión se corta con
   * un mensaje que lo dice.
   */
  public String rejection(JwtService.ParsedToken token) {
    AuthenticatedUser claims = token.user();
    if (token.platform()) {
      PlatformAdmin admin = platformAdminRepository.findById(claims.userId()).orElse(null);
      if (admin == null || !Boolean.TRUE.equals(admin.getIsActive())) return CLOSED;
      int current = admin.getTokenVersion() == null ? 0 : admin.getTokenVersion();
      return current == token.version() ? null : CLOSED;
    }

    User user = userRepository.findByIdScoped(claims.userId()).orElse(null);
    if (user == null || !Boolean.TRUE.equals(user.getIsActive())) return CLOSED;
    if (!claims.businessId().equals(user.getBusinessId())) return CLOSED;
    int current = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    if (current != token.version()) return CLOSED;

    Business business = businessRepository.findById(user.getBusinessId()).orElse(null);
    if (business == null) return CLOSED;
    if (business.isBlocked()) return blockedMessage(business, platformProperties);
    return null;
  }

  private static final String CLOSED = "Tu sesión venció o se cerró. Volvé a ingresar.";

  /** El mensaje que ve alguien de un café suspendido o dado de baja. */
  public static String blockedMessage(Business business, PlatformProperties properties) {
    if (business.isBlockedForPayment()) {
      // V16 — suspendido solo por falta de pago: el dueño puede entrar a pagar.
      return "Este café está suspendido por falta de pago de la suscripción. "
          + "El dueño puede entrar y pagarla desde la pantalla Suscripción.";
    }
    String what = business.getStatus() == BusinessStatus.CANCELLED ? "dado de baja" : "suspendido";
    String contact =
        properties.supportContact() != null && !properties.supportContact().isBlank()
            ? properties.supportContact()
            : "quien te dio acceso a COFFE_APP";
    String reason =
        business.getStatusReason() != null && !business.getStatusReason().isBlank()
            ? " (" + business.getStatusReason().trim() + ")"
            : "";
    return "Este café está " + what + reason + ". Comunicate con " + contact + ".";
  }
}
