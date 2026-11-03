package com.cofeapp.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** El token es el que llegó en el link del mail (en claro); el backend lo compara contra su hash. */
@Getter
@Setter
public class ResetPasswordRequest {

  @NotBlank
  private String token;

  @NotBlank
  @Size(min = 8, max = 72, message = "La contraseña nueva tiene que tener al menos 8 caracteres.")
  private String newPassword;
}
