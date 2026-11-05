package com.cofeapp.backend.billing;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Avisos de Mercado Pago sobre las suscripciones (V16): tópicos
 * "subscription_preapproval" (se confirmó / canceló una suscripción) y
 * "subscription_authorized_payment" (se hizo un débito). Público (Mercado
 * Pago no manda nuestro JWT) — ver SecurityConfig.
 *
 * <p>Es seguro aunque cualquiera pueda llamarlo: el aviso solo dispara
 * "andá a preguntarle a Mercado Pago", con el token de Fran. Siempre
 * responde 200 para que Mercado Pago no reintente de más. Y si se pierde
 * un aviso, el proceso de fondo ({@link BillingJob}) se pone al día solo.
 *
 * <p>Se configura en la cuenta de Mercado Pago de Fran (Tus integraciones →
 * Webhooks): {@code https://<dominio>/api/v1/webhooks/mercadopago-suscripciones},
 * eventos "Planes y suscripciones".
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class BillingWebhookController {

  private final BillingService billingService;

  @PostMapping("/webhooks/mercadopago-suscripciones")
  public ResponseEntity<Void> receive(
      @RequestParam(name = "type", required = false) String typeParam,
      @RequestParam(name = "data.id", required = false) String dataIdParam,
      @RequestBody(required = false) JsonNode body) {
    String type = typeParam;
    String id = dataIdParam;
    if (body != null) {
      if (type == null || type.isBlank()) type = text(body, "type");
      if (id == null || id.isBlank()) {
        JsonNode dataId = body.path("data").path("id");
        if (!dataId.isMissingNode() && !dataId.isNull()) id = dataId.asText();
      }
    }
    try {
      billingService.handleWebhook(type, id);
    } catch (RuntimeException ex) {
      log.warn("Error procesando aviso de suscripción de Mercado Pago ({} {}): {}", type, id, ex.getMessage());
    }
    return ResponseEntity.ok().build();
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }
}
