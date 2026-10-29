package com.cofeapp.backend.auth;

import com.cofeapp.backend.auth.dto.AuthResponse;
import com.cofeapp.backend.auth.dto.ChangePasswordRequest;
import com.cofeapp.backend.auth.dto.ForgotPasswordRequest;
import com.cofeapp.backend.auth.dto.ResetPasswordRequest;
import com.cofeapp.backend.auth.dto.LoginRequest;
import com.cofeapp.backend.auth.dto.RegisterBusinessRequest;
import com.cofeapp.backend.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final PasswordResetService passwordResetService;

  // Público — declarado en SecurityConfig (permitAll para esta ruta).
  @PostMapping("/auth/register-business")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse registerBusiness(@Valid @RequestBody RegisterBusinessRequest request) {
    return authService.registerBusiness(request);
  }

  // Público — declarado en SecurityConfig (permitAll para esta ruta).
  @PostMapping("/auth/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }

  /**
   * Endpoint de humo para probar el checklist de aislamiento a mano: dos
   * tokens de dos negocios distintos tienen que devolver acá dos
   * businessId distintos, cada uno el propio.
   */
  /** V9 — cambiar la propia contraseña. Autenticado. Devuelve un token nuevo (las demás sesiones se cierran). */
  @PostMapping("/auth/change-password")
  public AuthResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
    return authService.changePassword(request);
  }

  /**
   * V9 — "Olvidé mi contraseña". Público. Responde 204 siempre (exista o no
   * el email) para no revelar qué emails están registrados.
   */
  @PostMapping("/auth/forgot-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    passwordResetService.requestReset(request.getEmail());
  }

  /** V9 — elegir la contraseña nueva con el token del link del mail. Público. */
  @PostMapping("/auth/reset-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
  }

  @GetMapping("/auth/me")
  public AuthenticatedUser me(@AuthenticationPrincipal AuthenticatedUser user) {
    return user;
  }
}
