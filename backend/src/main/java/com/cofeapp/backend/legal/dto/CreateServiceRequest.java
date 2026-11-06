package com.cofeapp.backend.legal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Formulario público del "Botón de arrepentimiento" / "Botón de baja" (V17).
 *
 * @param type WITHDRAWAL (arrepentimiento) o CANCELLATION (baja)
 */
public record CreateServiceRequest(
    @NotBlank @Pattern(regexp = "WITHDRAWAL|CANCELLATION", message = "Tipo de pedido inválido.") String type,
    @NotBlank(message = "Escribí tu nombre.") @Size(max = 150) String contactName,
    @NotBlank(message = "Escribí tu email.") @Email(message = "Escribí un email válido.") @Size(max = 255) String email,
    @Size(max = 20) String taxId,
    @Size(max = 150) String cafeName,
    @Size(max = 2000) String message) {}
