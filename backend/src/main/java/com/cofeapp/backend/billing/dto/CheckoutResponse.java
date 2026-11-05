package com.cofeapp.backend.billing.dto;

/** Adónde mandar al dueño para confirmar la suscripción en Mercado Pago. */
public record CheckoutResponse(String checkoutUrl) {}
