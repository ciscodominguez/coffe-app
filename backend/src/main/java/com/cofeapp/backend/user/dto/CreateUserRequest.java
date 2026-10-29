package com.cofeapp.backend.user.dto;

import com.cofeapp.backend.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserRequest {

  @NotBlank
  @Email
  @Size(max = 255)
  private String email;

  @NotBlank
  @Size(min = 8, max = 72, message = "password debe tener al menos 8 caracteres.")
  private String password;

  @NotBlank
  @Size(min = 1, max = 100)
  private String firstName;

  @NotBlank
  @Size(min = 1, max = 100)
  private String lastName;

  @NotNull private UserRole role;

  /** Local asignado. Si se omite, el usuario tiene acceso a todos los locales del negocio. */
  private String locationId;
}
