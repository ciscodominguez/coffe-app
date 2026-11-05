package com.cofeapp.backend.billing;

import com.cofeapp.backend.domain.Business;
import com.cofeapp.backend.domain.enums.BusinessStatus;
import java.time.Duration;
import java.time.Instant;

/**
 * En qué anda el cobro de un café (V16), calculado de sus fechas:
 *
 * <ul>
 *   <li>{@code EXEMPT}: "Sin cobro" (cortesía de Fran).
 *   <li>{@code TRIAL}: dentro de la prueba gratis.
 *   <li>{@code ACTIVE}: pago al día.
 *   <li>{@code PAST_DUE}: se venció (la prueba o lo pago) y está en los días
 *       de gracia: el dueño ve el aviso; al terminar, se suspende solo.
 *   <li>{@code SUSPENDED}: suspendido por falta de pago (el dueño puede
 *       entrar a pagar).
 *   <li>{@code BLOCKED}: suspendido o dado de baja A MANO por Fran.
 * </ul>
 *
 * @param accessUntil hasta cuándo puede usarlo sin pagar más (fin de la prueba o de lo pago)
 * @param suspendsAt cuándo se suspende solo si no paga (accessUntil + días de gracia)
 */
public record BillingState(String state, Instant accessUntil, Instant suspendsAt, long daysLeft) {

  public static BillingState of(Business business, int graceDays, Instant now) {
    Instant accessUntil = accessUntil(business);
    Instant suspendsAt = accessUntil == null ? null : accessUntil.plus(Duration.ofDays(graceDays));
    String state;
    if (business.getStatus() == BusinessStatus.CANCELLED
        || (business.getStatus() == BusinessStatus.SUSPENDED && !Boolean.TRUE.equals(business.getBillingSuspended()))) {
      state = "BLOCKED";
    } else if (business.isBlockedForPayment()) {
      state = "SUSPENDED";
    } else if (Boolean.TRUE.equals(business.getBillingExempt())) {
      state = "EXEMPT";
    } else if (accessUntil != null && now.isBefore(accessUntil)) {
      state = business.getPaidUntil() != null && now.isBefore(business.getPaidUntil()) ? "ACTIVE" : "TRIAL";
    } else {
      state = "PAST_DUE";
    }
    Instant target = "PAST_DUE".equals(state) ? suspendsAt : accessUntil;
    long daysLeft = target == null ? 0 : Math.max(0, (long) Math.ceil(Duration.between(now, target).toHours() / 24.0));
    return new BillingState(state, accessUntil, suspendsAt, daysLeft);
  }

  /** El mayor entre el fin de la prueba y hasta dónde está pago. */
  public static Instant accessUntil(Business business) {
    Instant trial = business.getTrialEndsAt();
    Instant paid = business.getPaidUntil();
    if (trial == null) return paid;
    if (paid == null) return trial;
    return paid.isAfter(trial) ? paid : trial;
  }
}
