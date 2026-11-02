package com.cofeapp.backend.mercadopago.dto;

import com.cofeapp.backend.domain.MpQrCharge;
import com.cofeapp.backend.domain.enums.QrChargeStatus;
import com.cofeapp.backend.domain.enums.QrMode;
import com.cofeapp.backend.order.dto.OrderResponse;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un cobro con QR. {@code qrData} es el contenido del QR a dibujar (modo
 * DYNAMIC); {@code qrImageUrl} es la imagen del QR fijo del local (modo
 * STATIC). {@code order} viaja con el pedido actualizado, así la pantalla
 * de cobro refleja el pago apenas se confirma.
 */
public record QrChargeResponse(
    String id,
    String orderId,
    String locationId,
    QrMode mode,
    BigDecimal amount,
    QrChargeStatus status,
    String qrData,
    String qrImageUrl,
    String statusMessage,
    Instant expiresAt,
    Instant createdAt,
    Instant resolvedAt,
    OrderResponse order) {

  public static QrChargeResponse from(MpQrCharge charge, String qrImageUrl, OrderResponse order) {
    boolean pending = charge.getStatus() == QrChargeStatus.PENDING;
    return new QrChargeResponse(
        charge.getId(),
        charge.getOrderId(),
        charge.getLocationId(),
        charge.getMode(),
        charge.getAmount(),
        charge.getStatus(),
        // Una vez resuelto, el QR ya no sirve: no se vuelve a mostrar.
        pending ? charge.getQrData() : null,
        pending ? qrImageUrl : null,
        charge.getStatusMessage(),
        charge.getExpiresAt(),
        charge.getCreatedAt(),
        charge.getResolvedAt(),
        order);
  }
}
