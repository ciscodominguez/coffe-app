package com.cofeapp.backend.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Proceso de fondo del cobro (V16): cada hora (configurable) se pone al día
 * con Mercado Pago y suspende a los cafés con los días de gracia vencidos.
 * En los tests está apagado y se llama a mano.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.billing.job-enabled", havingValue = "true", matchIfMissing = true)
public class BillingJob {

  private final BillingService billingService;

  @Scheduled(initialDelay = 60_000, fixedDelayString = "${app.billing.job-interval-ms:3600000}")
  public void run() {
    billingService.runChecks();
  }
}
