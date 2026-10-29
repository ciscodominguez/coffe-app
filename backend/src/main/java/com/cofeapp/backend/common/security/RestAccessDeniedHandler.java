package com.cofeapp.backend.common.security;

import com.cofeapp.backend.common.exception.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 403 con el mismo shape que el resto de la API. Se dispara cuando
 * {@code @PreAuthorize("hasAnyAuthority(...)")}
 * rechaza a un usuario ya autenticado pero con un rol no permitido.
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
      throws IOException {
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    ApiErrorResponse body =
        new ApiErrorResponse(
            HttpStatus.FORBIDDEN.value(),
            "No tenés permisos para acceder a este recurso.",
            "Forbidden");
    objectMapper.writeValue(response.getWriter(), body);
  }
}
