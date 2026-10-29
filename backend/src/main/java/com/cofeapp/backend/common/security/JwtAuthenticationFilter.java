package com.cofeapp.backend.common.security;

import com.cofeapp.backend.common.context.TenantContext;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Combina, en un solo filtro de Servlet, la autenticación (validar el
 * JWT) y la apertura del contexto de tenant — y, si no viene token, no
 * hace ninguna de las dos cosas (la request sigue "cruda").
 *
 * <p>Alcanza con un solo filtro porque {@link TenantContext#open}
 * simplemente popula un {@link ThreadLocal} antes de seguir la cadena de
 * filtros, y {@link TenantContext#clear} lo limpia en un {@code finally}
 * sin importar cómo termine la request — no hace falta "envolver" la
 * ejecución del handler en nada.
 *
 * <p>Publicidad de rutas: no hay una marca de "ruta pública" por handler
 * — {@code SecurityConfig} declara {@code permitAll()} para
 * {@code /auth/register-business} y {@code /auth/login} por URL. Este
 * filtro igual intenta validar el token si viene uno (una ruta pública con
 * un Authorization header no rompe nada: si el token es válido, el
 * contexto de tenant se abre igual — que una ruta sea pública no prohíbe
 * mandar un token). Si NO viene token, se sigue sin autenticar: una ruta
 * pública funciona igual, y una protegida termina en 401 vía
 * {@link RestAuthenticationEntryPoint}.
 *
 * <p>A propósito NO es {@code @Component}: si lo fuera, Spring Boot lo
 * registraría automáticamente como filtro de Servlet genérico (vía
 * {@code FilterRegistrationBean}) ADEMÁS de insertarlo a mano en la cadena
 * de Spring Security con {@code addFilterBefore(...)} (ver
 * {@code SecurityConfig}) — quedaría corriendo dos veces por request. Lo
 * instancia directamente {@code SecurityConfig}, con sus dependencias
 * inyectadas por constructor como cualquier otro bean.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  /** Atributo de request donde se deja el motivo específico de un 401, para que {@link RestAuthenticationEntryPoint} lo use. */
  public static final String AUTH_ERROR_ATTRIBUTE = "com.cofeapp.backend.authError";

  private final JwtService jwtService;
  private final TenantContext tenantContext;
  private final SessionValidator sessionValidator;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String token = extractBearerToken(request);

    if (token != null) {
      try {
        JwtService.ParsedToken parsed = jwtService.parse(token);
        SessionValidator.Verdict verdict = sessionValidator.check(parsed);
        if (verdict.paymentOnly() && !isPaymentPath(request)) {
          // V16 — dueño de un café suspendido por falta de pago: puede
          // entrar, pero solo a pagar. 402 (no 401): el frontend lo manda a
          // la pantalla de Suscripción en vez de cerrarle la sesión.
          writePaymentRequired(response, verdict.message());
          return;
        }
        String rejection = verdict.paymentOnly() ? null : verdict.message();
        if (rejection != null) {
          // Firma y vencimiento OK, pero la sesión ya no vale: le cambiaron
          // la contraseña, lo dieron de baja, el café está suspendido (V15)…
          // Mismo trato que un token vencido: 401 y el frontend manda al login.
          throw new SessionRejectedException(rejection);
        }
        AuthenticatedUser user = parsed.user();

        var authorities = List.of(new SimpleGrantedAuthority(user.role()));
        var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // V15: el superadmin no es de ningún café — no se abre contexto de
        // tenant (y SecurityConfig solo lo deja entrar a /platform/**).
        if (!parsed.platform()) {
          tenantContext.open(
              new TenantContext.TenantStore(
                  user.businessId(), user.locationId(), user.userId(), user.role(), user.email()));
        }
      } catch (SessionRejectedException ex) {
        SecurityContextHolder.clearContext();
        request.setAttribute(AUTH_ERROR_ATTRIBUTE, ex.getMessage());
      } catch (JwtException | IllegalArgumentException ex) {
        SecurityContextHolder.clearContext();
        request.setAttribute(AUTH_ERROR_ATTRIBUTE, "Tu sesión venció o se cerró. Volvé a ingresar.");
      }
    }

    try {
      filterChain.doFilter(request, response);
    } finally {
      tenantContext.clear();
    }
  }

  /** V16 — lo único que puede usar el dueño de un café suspendido por falta de pago. */
  private static boolean isPaymentPath(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return path.equals("/subscription")
        || path.startsWith("/subscription/")
        || path.startsWith("/auth/")
        || path.startsWith("/support/"); // V18 — pedir ayuda
  }

  private static void writePaymentRequired(HttpServletResponse response, String message) throws IOException {
    response.setStatus(402);
    response.setContentType("application/json;charset=UTF-8");
    String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
    response.getWriter().write("{\"statusCode\":402,\"message\":\"" + escaped + "\",\"error\":\"Payment Required\"}");
  }

  /** La sesión ya no vale, con el motivo para mostrar. */
  private static final class SessionRejectedException extends RuntimeException {
    SessionRejectedException(String message) {
      super(message);
    }
  }

  private String extractBearerToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header == null) return null;
    String[] parts = header.split(" ", 2);
    return parts.length == 2 && "Bearer".equalsIgnoreCase(parts[0]) ? parts[1] : null;
  }
}
