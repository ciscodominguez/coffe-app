package com.cofeapp.backend.platform.dto;

import com.cofeapp.backend.domain.enums.BusinessPlan;
import com.cofeapp.backend.domain.enums.BusinessStatus;
import com.cofeapp.backend.domain.enums.FiscalCondition;
import java.time.Instant;

/** Un café cliente en la lista del panel de superadmin (V15). */
public record BusinessSummaryResponse(
    String id,
    String legalName,
    String tradeName,
    String taxId,
    FiscalCondition fiscalCondition,
    BusinessPlan plan,
    BusinessStatus status,
    String statusReason,
    Instant createdAt,
    String ownerName,
    String ownerEmail,
    long locations,
    long activeUsers,
    long ordersLast30Days,
    Instant lastOrderAt,
    // V16 — cobro: estado (ver BillingState), hasta cuándo puede usarlo, cuándo se suspende solo.
    String billingState,
    Instant accessUntil,
    Instant suspendsAt,
    boolean billingExempt,
    String subscriptionStatus) {}
