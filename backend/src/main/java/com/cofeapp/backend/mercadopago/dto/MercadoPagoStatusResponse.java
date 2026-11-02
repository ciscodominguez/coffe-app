package com.cofeapp.backend.mercadopago.dto;

import com.cofeapp.backend.domain.enums.QrMode;
import java.time.Instant;
import java.util.List;

/** Estado de la conexión con Mercado Pago del negocio. Nunca incluye el token. */
public record MercadoPagoStatusResponse(
    boolean connected,
    String nickname,
    String mpUserId,
    boolean testMode,
    Instant connectedAt,
    List<LocationQrStatus> locations) {

  public record LocationQrStatus(
      String locationId, String locationName, QrMode qrMode, boolean configured, String qrImageUrl) {}
}
