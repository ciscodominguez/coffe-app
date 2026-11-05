package com.cofeapp.backend.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cobro de la suscripción (V16 — ver README, paso 3.2).
 *
 * @param mpAccessToken Access Token de la cuenta de Mercado Pago de Fran, donde
 *     entra la plata de las suscripciones. Vacío = todavía no se puede
 *     suscribir nadie (la prueba gratis y los avisos funcionan igual).
 * @param jobEnabled el proceso de fondo que revisa pagos y suspende por falta
 *     de pago (en los tests se llama a mano).
 * @param jobIntervalMs cada cuánto corre (por defecto, cada hora).
 */
@ConfigurationProperties(prefix = "app.billing")
public record BillingProperties(String mpAccessToken, Boolean jobEnabled, Long jobIntervalMs) {

  public BillingProperties {
    if (jobEnabled == null) jobEnabled = true;
    if (jobIntervalMs == null || jobIntervalMs < 60_000) jobIntervalMs = 3_600_000L;
  }

  public boolean hasMercadoPago() {
    return mpAccessToken != null && !mpAccessToken.isBlank();
  }
}
