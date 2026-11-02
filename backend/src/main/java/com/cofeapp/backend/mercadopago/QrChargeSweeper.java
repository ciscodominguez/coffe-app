package com.cofeapp.backend.mercadopago;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cada {@code app.mercadopago.sync-interval-ms} (15 s por defecto) revisa
 * los cobros con QR que siguen esperando. Es la red de seguridad para
 * cuando nadie tiene abierta la ventana de cobro (se cerró, se cortó la
 * conexión de la tablet) y todavía no hay webhook. En los tests se apaga
 * ({@code app.mercadopago.sweeper-enabled: false}) y se llama a mano.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mercadopago.sweeper-enabled", havingValue = "true", matchIfMissing = true)
public class QrChargeSweeper {

  private final QrChargeService qrChargeService;

  @Scheduled(initialDelay = 20_000, fixedDelayString = "${app.mercadopago.sync-interval-ms:15000}")
  public void syncPendingCharges() {
    qrChargeService.syncAllPending();
  }
}
