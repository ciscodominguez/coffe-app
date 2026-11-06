package com.cofeapp.backend.legal.dto;

import java.time.Instant;

/** Un pedido de arrepentimiento o baja, como lo ve Fran en el panel (V17). */
public record ServiceRequestResponse(
    String id,
    String code,
    String type,
    String businessId,
    String businessName,
    String contactName,
    String email,
    String taxId,
    String cafeName,
    String message,
    String status,
    String result,
    String adminNote,
    Instant createdAt,
    Instant handledAt) {}
