package com.cofeapp.backend.support.dto;

import jakarta.validation.constraints.Size;

/** Lo que manda el botón de soporte: qué pasó (opcional) y datos de diagnóstico. */
public record CreateSupportRequest(
    @Size(max = 1000) String message,
    @Size(max = 255) String page,
    @Size(max = 1000) String lastError,
    @Size(max = 255) String userAgent) {}
