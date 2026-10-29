package com.cofeapp.backend.config;

import com.cofeapp.backend.common.context.TenantContext;
import com.cofeapp.backend.common.security.JwtAuthenticationFilter;
import com.cofeapp.backend.common.security.JwtService;
import com.cofeapp.backend.common.security.SessionValidator;
import com.cofeapp.backend.common.security.RestAccessDeniedHandler;
import com.cofeapp.backend.common.security.RestAuthenticationEntryPoint;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuración de seguridad: autenticación por JWT, rutas públicas, CORS
 * y RBAC.
 *
 * <p>Rutas públicas: acá se declaran por URL ({@code permitAll()}) en vez
 * de con una marca por handler — es lo más idiomático en Spring Security
 * para una API con solo dos endpoints públicos. Todo lo demás exige un
 * JWT válido.
 *
 * <p>RBAC por rol: NO se configura acá con {@code .hasAnyAuthority(...)}
 * por ruta — cada controller usa {@code @PreAuthorize("hasAnyAuthority(...)")}
 * método por método, con granularidad por endpoint. Ver
 * {@code @EnableMethodSecurity} más abajo, que lo habilita.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtService jwtService;
  private final TenantContext tenantContext;
  private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
  private final RestAccessDeniedHandler restAccessDeniedHandler;
  private final SessionValidator sessionValidator;

  @Value("${app.cors.origin}")
  private String corsOrigin;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(restAuthenticationEntryPoint)
                    .accessDeniedHandler(restAccessDeniedHandler))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    // Únicos dos endpoints públicos del sistema.
                    .requestMatchers(
                        "/auth/register-business",
                        "/auth/login",
                        // "Olvidé mi contraseña" (V9): llegan sin sesión, igual que el login.
                        "/auth/forgot-password",
                        "/auth/reset-password")
                    .permitAll()
                    // El handshake de WebSocket (GET /ws/orders) NO lleva
                    // el JWT en el header Authorization -- un navegador no
                    // puede setear headers custom en el upgrade a
                    // WebSocket -- así que JwtAuthenticationFilter nunca
                    // ve un token acá y anyRequest().authenticated() de
                    // abajo lo rechazaría con 401 antes de que la request
                    // llegue siquiera a WebSocketAuthHandshakeInterceptor,
                    // que es quien de verdad autentica esta conexión
                    // (leyendo el token del query param). Por eso este
                    // path queda afuera de la autenticación HTTP estándar
                    // -- no es una ruta insegura, tiene su propia capa de
                    // auth un nivel más abajo, específica de WebSocket.
                    .requestMatchers("/ws/orders")
                    .permitAll()
                    // Imágenes de producto servidas como estáticos (ver
                    // WebMvcConfig#addResourceHandlers) — públicas a
                    // propósito: un <img src> del navegador no manda el
                    // header Authorization, así que exigir JWT acá
                    // rompería mostrarlas. No es información sensible.
                    .requestMatchers("/uploads/**")
                    .permitAll()
                    // Aviso de Mercado Pago (V10): MP no manda nuestro JWT.
                    // Solo dispara una consulta a MP con el token del negocio;
                    // ver MercadoPagoWebhookController sobre por qué es seguro.
                    .requestMatchers("/webhooks/mercadopago")
                    .permitAll()
                    // V16 — avisos de las suscripciones (misma lógica: solo
                    // disparan una consulta a MP con el token de Fran).
                    .requestMatchers("/webhooks/mercadopago-suscripciones")
                    .permitAll()
                    // V17 — formulario público del "Botón de arrepentimiento" /
                    // "Botón de baja" (Defensa del Consumidor: sin tener que
                    // entrar) y la versión vigente de los términos.
                    .requestMatchers("/legal/requests", "/legal/version")
                    .permitAll()
                    // V18 — a quién pedir ayuda (también desde el login).
                    .requestMatchers("/support/info")
                    .permitAll()
                    // Endpoint de salud (servidor): solo responde UP/DOWN,
                    // sin detalles. Lo consultan Docker y el monitor externo.
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    // V15 — panel de superadmin: SOLO con token de plataforma.
                    .requestMatchers("/platform/**")
                    .hasAuthority("SUPERADMIN")
                    // Y el token de plataforma no entra a nada de un café:
                    // ahí no hay contexto de tenant (no se aplica el filtro
                    // de aislamiento), así que se exige un rol de café.
                    .anyRequest()
                    .hasAnyAuthority("OWNER", "ADMIN", "SUPERVISOR", "WAITER", "KITCHEN"))
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtService, tenantContext, sessionValidator),
            UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of(corsOrigin));
    configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  /**
   * bcrypt con 12 rondas.
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  // DaoAuthenticationProvider no se usa: este proyecto no pasa por
  // AuthenticationManager para el login (AuthService valida la contraseña
  // a mano contra el hash — ver AuthService.login). Se deja fuera de
  // la config de beans a propósito, solo mencionado acá por si en una
  // etapa futura hiciera falta un login por header Basic (no lo hay hoy).
}
