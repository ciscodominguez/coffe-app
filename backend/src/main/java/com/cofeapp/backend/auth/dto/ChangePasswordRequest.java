package com.cofeapp.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Cambiar la propia contraseña (hay que saber la actual). Mismo mínimo que al crear un usuario. */
@Getter
@Setter
public class ChangePasswordRequest {

  @NotBlank
  private String currentPassword;

  @NotBlank
  @Size(min = 8, max = 72, message = "La contraseña nueva tiene que tener al menos 8 caracteres.")
  private String newPassword;
}
