package com.cofeapp.backend.mercadopago.dto;

import com.cofeapp.backend.domain.enums.QrMode;

/**
 * Para la ventana de cobro: si en este local se puede cobrar con QR de
 * Mercado Pago, con qué modo, y si no, por qué (para mostrarlo).
 */
public record QrAvailabilityResponse(boolean available, QrMode mode, boolean testMode, String reason) {}
