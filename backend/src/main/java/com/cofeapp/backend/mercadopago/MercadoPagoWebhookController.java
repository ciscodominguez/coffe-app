package com.cofeapp.backend.mercadopago;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aviso de Mercado Pago cuando cambia una orden (tópico "order"). Es
 * público (MP no manda nuestro JWT) — ver SecurityConfig.
 *
 * <p>Es seguro aunque cualquiera pueda llamarlo: el aviso solo dispara
 * "andá a preguntarle a Mercado Pago cómo está esta orden", con el token
 * del negocio dueño de ese cobro. Un aviso falso no puede marcar nada como
 * pagado. Siempre responde 200 para que MP no reintente de más.
 *
 * <p>Hoy, sin la app publicada, MP no tiene adónde avisar: la ventana de
 * cobro y el proceso de fondo alcanzan. Cuando se publique, se configura
 * en el panel de Mercado Pago: {@code https://<dominio>/api/v1/webhooks/mercadopago},
 * evento "Order (Mercado Pago)".
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class MercadoPagoWebhookController {

  private final QrChargeService qrChargeService;

  @PostMapping("/webhooks/mercadopago")
  public ResponseEntity<Void> receive(
      @RequestParam(name = "data.id", required = false) String dataIdParam,
      @RequestBody(required = false) JsonNode body) {
    String mpOrderId = dataIdParam;
    if ((mpOrderId == null || mpOrderId.isBlank()) && body != null) {
      JsonNode id = body.path("data").path("id");
      if (!id.isMissingNode() && !id.isNull()) mpOrderId = id.asText();
    }
    try {
      qrChargeService.handleWebhook(mpOrderId);
    } catch (RuntimeException ex) {
      log.warn("Error procesando aviso de Mercado Pago ({}): {}", mpOrderId, ex.getMessage());
    }
    return ResponseEntity.ok().build();
  }
}
