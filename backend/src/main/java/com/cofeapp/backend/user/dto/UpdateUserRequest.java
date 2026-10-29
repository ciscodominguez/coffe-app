package com.cofeapp.backend.user.dto;

import com.cofeapp.backend.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Todos los campos son opcionales (incluidos {@code password} e
 * {@code isActive}). Omitir {@code password} significa "no cambiar la
 * contraseña".
 */
@Getter
@Setter
public class UpdateUserRequest {

  @Email
  @Size(max = 255)
  private String email;

  @Size(min = 1, max = 100)
  private String firstName;

  @Size(min = 1, max = 100)
  private String lastName;

  private UserRole role;

  private String locationId;

  @Size(min = 8, max = 72, message = "password debe tener al menos 8 caracteres.")
  private String password;

  private Boolean isActive;
}
