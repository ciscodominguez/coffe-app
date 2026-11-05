package com.cofeapp.backend.billing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/**
 * Configuración del cobro que edita Fran en el panel (V16).
 *
 * @param mercadoPagoConfigured solo lectura: si está BILLING_MP_ACCESS_TOKEN en el .env
 * @param supportWhatsapp V18 — WhatsApp del botón de soporte (vacío = sin WhatsApp)
 */
public record PlatformSettingsDto(
    @DecimalMin(value = "1", message = "El precio tiene que ser mayor a cero.") BigDecimal monthlyPrice,
    @NotNull @Min(value = 0, message = "Los días de prueba no pueden ser negativos.") @Max(90) Integer trialDays,
    @NotNull @Min(value = 0, message = "Los días de gracia no pueden ser negativos.") @Max(60) Integer graceDays,
    Boolean mercadoPagoConfigured,
    @Pattern(regexp = "^$|^\\d{8,15}$", message = "El WhatsApp va con código de país y sin espacios ni signos, ej. 5492235550000.")
        String supportWhatsapp) {}
