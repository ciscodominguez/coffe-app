package com.cofeapp.backend.mercadopago;

import com.cofeapp.backend.mercadopago.dto.CreateQrChargeRequest;
import com.cofeapp.backend.mercadopago.dto.QrChargeResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Cobro con QR de Mercado Pago — mismos roles que registrar un cobro común (OrderController#addPayment). */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('WAITER', 'SUPERVISOR', 'ADMIN', 'OWNER')")
public class QrChargeController {

  private final QrChargeService qrChargeService;

  /** Devuelve 200 (no 201) porque puede devolver un cobro que ya estaba esperando. */
  @PostMapping("/orders/{orderId}/qr-charges")
  public QrChargeResponse create(
      @PathVariable String orderId, @Valid @RequestBody(required = false) CreateQrChargeRequest request) {
    return qrChargeService.create(orderId, request);
  }

  @GetMapping("/qr-charges/{id}")
  public QrChargeResponse get(@PathVariable String id) {
    return qrChargeService.get(id);
  }

  @PostMapping("/qr-charges/{id}/cancel")
  public QrChargeResponse cancel(@PathVariable String id) {
    return qrChargeService.cancel(id);
  }
}
