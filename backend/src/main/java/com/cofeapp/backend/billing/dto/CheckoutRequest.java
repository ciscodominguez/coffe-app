package com.cofeapp.backend.billing.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El email de la cuenta de Mercado Pago con la que va a pagar el dueño. */
public record CheckoutRequest(
    @NotBlank(message = "Escribí el email de tu cuenta de Mercado Pago.")
        @Email(message = "Escribí un email válido.")
        @Size(max = 255)
        String payerEmail) {}
