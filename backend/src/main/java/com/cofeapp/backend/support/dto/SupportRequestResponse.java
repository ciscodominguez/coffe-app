package com.cofeapp.backend.support.dto;

import java.time.Instant;

/** Un pedido de ayuda como lo ve Fran en el panel (V18). */
public record SupportRequestResponse(
    String id,
    String businessId,
    String businessName,
    String userName,
    String userRole,
    String page,
    String message,
    String lastError,
    String userAgent,
    String status,
    String adminNote,
    Instant createdAt,
    Instant handledAt) {}
