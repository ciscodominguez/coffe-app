package com.cofeapp.backend.common.security;

import com.cofeapp.backend.domain.Location;
import com.cofeapp.backend.location.LocationRepository;
import io.jsonwebtoken.JwtException;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Autenticación de la conexión WebSocket: valida el JWT y rechaza ANTES
 * de que la conexión quede establecida (nunca después de conectar, que ya
 * es tarde: el socket ya está abierto).
 *
 * <p>Se rechaza en {@link #beforeHandshake} devolviendo {@code false} —
 * eso hace que el upgrade HTTP -> WebSocket ni siquiera se complete, el
 * cliente recibe un 401/403 llano en vez de un socket abierto que hay que
 * cerrar después.
 *
 * <p>El token viaja como query param ({@code ?token=...}) en vez de en un
 * header, porque la WebSocket API nativa del browser no permite mandar
 * headers custom en el upgrade — es la forma estándar de resolver esto con
 * un cliente STOMP puro.
 *
 * <p>Resolución de local por defecto, para sockets de OWNER/ADMIN sin
 * {@code locationId} en el JWT (acceso a todos
 * los locales del negocio) — se les asigna el local más antiguo del
 * negocio como "room" de esa conexión. La consulta usa {@link
 * LocationRepository#findFirstByBusinessIdOrderByCreatedAtAsc}, que filtra
 * por {@code businessId} EXPLÍCITO en vez de depender del {@code @Filter}
 * de Hibernate habilitado por request: el handshake de WebSocket corre por
 * fuera de la cadena de {@code HandlerInterceptor} de Spring MVC (no pasa
 * por {@code TenantFilterInterceptor}), así que acá no hay filtro de
 * tenant habilitado ni contexto en el {@code ThreadLocal} de {@link
 * com.cofeapp.backend.common.context.TenantContext} — filtrar a mano con
 * el {@code businessId} ya extraído del token es la forma segura de
 * resolver esto sin depender de ese mecanismo.
 */
@Component
@RequiredArgsConstructor
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

  public static final String SESSION_ATTR_BUSINESS_ID = "businessId";
  public static final String SESSION_ATTR_LOCATION_ID = "locationId";
  public static final String SESSION_ATTR_USER_ID = "userId";
  public static final String SESSION_ATTR_ROLE = "role";

  private final JwtService jwtService;
  private final LocationRepository locationRepository;
  private final SessionValidator sessionValidator;

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {

    String token = extractToken(request);
    if (token == null) {
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }

    AuthenticatedUser user;
    try {
      JwtService.ParsedToken parsed = jwtService.parse(token);
      // V15: el superadmin no escucha pedidos de ningún café.
      if (parsed.platform() || !sessionValidator.isCurrent(parsed)) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
      }
      user = parsed.user();
    } catch (JwtException | IllegalArgumentException ex) {
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }

    String locationId = user.locationId();
    if (locationId == null) {
      Optional<Location> defaultLocation =
          locationRepository.findFirstByBusinessIdOrderByCreatedAtAsc(user.businessId());
      if (defaultLocation.isEmpty()) {
        // No debería pasar (todo negocio tiene al menos un local desde el
        // alta), pero mejor rechazar explícito que dejar la conexión sin
        // room asignado.
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return false;
      }
      locationId = defaultLocation.get().getId();
    }

    attributes.put(SESSION_ATTR_BUSINESS_ID, user.businessId());
    attributes.put(SESSION_ATTR_LOCATION_ID, locationId);
    attributes.put(SESSION_ATTR_USER_ID, user.userId());
    attributes.put(SESSION_ATTR_ROLE, user.role());
    return true;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
    // Nada que hacer acá — toda la autenticación se resuelve (o rechaza) en beforeHandshake.
  }

  private String extractToken(ServerHttpRequest request) {
    return UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("token");
  }
}
