package com.cofeapp.backend.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * V19 — datos de un pedido que vino por una app de delivery (carga manual,
 * Fase A de docs/INTEGRACION-APPS-DELIVERY.md).
 *
 * @param channelId la app (canal de venta) del local
 * @param orderRef número del pedido en la app; no se puede repetir
 * @param customerName nombre del cliente (opcional)
 * @param pickupAt a qué hora pasa el repartidor (opcional)
 * @param paidByApp true (o vacío) = lo cobró la app; false = lo cobra el café
 */
public record ChannelOrderInput(
    @NotBlank(message = "Elegí la app.") String channelId,
    @NotBlank(message = "Poné el número del pedido en la app.") @Size(max = 40, message = "El número del pedido es muy largo.")
        String orderRef,
    @Size(max = 80, message = "El nombre del cliente es muy largo.") String customerName,
    Instant pickupAt,
    Boolean paidByApp) {}
