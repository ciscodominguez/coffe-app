package com.cofeapp.backend.common.security;

import com.cofeapp.backend.common.exception.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Responde 401 con el mismo shape {@code {statusCode, message, error}} que
 * el resto de la API (ver {@code GlobalExceptionHandler}) — necesario
 * porque esto corre en la cadena de filtros de Spring Security, ANTES de
 * que un {@code @RestControllerAdvice} pueda intervenir.
 *
 * <p>Mensaje: usa el que {@link JwtAuthenticationFilter} haya dejado en
 * {@link JwtAuthenticationFilter#AUTH_ERROR_ATTRIBUTE} si el request traía
 * un token inválido/expirado; si no hay ninguno (no se mandó ningún
 * token), usa el mensaje por defecto.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
      throws IOException {
    String message =
        (String) request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
    if (message == null) {
      message = "Token de autenticación no provisto.";
    }

    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    ApiErrorResponse body = new ApiErrorResponse(HttpStatus.UNAUTHORIZED.value(), message, "Unauthorized");
    objectMapper.writeValue(response.getWriter(), body);
  }
}
