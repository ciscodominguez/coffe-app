package com.cofeapp.backend.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Cómo está la suscripción de un café (V16). Lo ven el dueño (pantalla
 * Suscripción) y Fran (panel de superadmin).
 *
 * @param state EXEMPT, TRIAL, ACTIVE, PAST_DUE, SUSPENDED o BLOCKED (ver BillingState)
 * @param subscriptionStatus lo que dice Mercado Pago de la suscripción: pending, authorized, paused, cancelled (o null)
 * @param canSubscribe si se puede tocar "Suscribirme" ahora
 * @param unavailableReason por qué no, si no se puede
 */
public record BillingStatusResponse(
    String state,
    Instant trialEndsAt,
    Instant paidUntil,
    Instant accessUntil,
    Instant suspendsAt,
    long daysLeft,
    BigDecimal monthlyPrice,
    int graceDays,
    String subscriptionStatus,
    String payerEmail,
    boolean canSubscribe,
    String unavailableReason,
    boolean billingExempt,
    List<Payment> payments) {

  public record Payment(Instant chargedAt, BigDecimal amount, String status, String statusDetail, Instant coversUntil) {}
}
