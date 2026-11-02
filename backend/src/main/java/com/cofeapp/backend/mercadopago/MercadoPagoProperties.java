package com.cofeapp.backend.mercadopago;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.mercadopago.*} en application.yml.
 *
 * @param apiBaseUrl dirección de la API de Mercado Pago.
 * @param tokenEncryptionKey frase con la que se cifran los Access Token guardados (ver SecretCipher).
 * @param chargeExpirationMinutes cuánto espera un cobro con QR antes de vencer (Mercado Pago admite hasta 15).
 * @param syncIntervalMs cada cuánto el proceso de fondo revisa los cobros pendientes.
 * @param sweeperEnabled si corre ese proceso de fondo (en los tests se apaga).
 */
@ConfigurationProperties(prefix = "app.mercadopago")
public record MercadoPagoProperties(
    String apiBaseUrl,
    String tokenEncryptionKey,
    Integer chargeExpirationMinutes,
    Long syncIntervalMs,
    Boolean sweeperEnabled) {

  public MercadoPagoProperties {
    if (apiBaseUrl == null || apiBaseUrl.isBlank()) apiBaseUrl = "https://api.mercadopago.com";
    if (chargeExpirationMinutes == null || chargeExpirationMinutes < 1 || chargeExpirationMinutes > 15) {
      chargeExpirationMinutes = 15;
    }
    if (syncIntervalMs == null || syncIntervalMs < 1000) syncIntervalMs = 15000L;
    if (sweeperEnabled == null) sweeperEnabled = true;
  }
}
