package com.cofeapp.backend.auth.dto;

import com.cofeapp.backend.domain.enums.FiscalCondition;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Alta de un negocio nuevo (tenant) + su primer usuario (OWNER), en una
 * sola operación. Es el único endpoint del sistema que crea un
 * {@code Business}.
 */
@Getter
@Setter
public class RegisterBusinessRequest {

  @NotBlank
  @Size(min = 2, max = 150)
  private String legalName;

  @Size(max = 150)
  private String tradeName;

  /** CUIT, formato NN-NNNNNNNN-N o solo dígitos (11 caracteres). */
  @NotBlank
  @Pattern(
      regexp = "^\\d{2}-?\\d{8}-?\\d{1}$",
      message = "taxId debe ser un CUIT válido (NN-NNNNNNNN-N u 11 dígitos).")
  private String taxId;

  @NotNull private FiscalCondition fiscalCondition;

  @NotBlank
  @Email
  @Size(max = 255)
  private String ownerEmail;

  @NotBlank
  @Size(min = 8, max = 72, message = "ownerPassword debe tener al menos 8 caracteres.")
  private String ownerPassword;

  @NotBlank
  @Size(min = 1, max = 100)
  private String ownerFirstName;

  @NotBlank
  @Size(min = 1, max = 100)
  private String ownerLastName;

  /** V17 — la casilla "Leí y acepto los Términos y la Política de privacidad". */
  @NotNull(message = "Tenés que aceptar los Términos y condiciones y la Política de privacidad.")
  @AssertTrue(message = "Tenés que aceptar los Términos y condiciones y la Política de privacidad.")
  private Boolean acceptTerms;
}
