package com.cofeapp.backend.legal.dto;

import java.time.Instant;

/** Lo que ve quien mandó el formulario: su código de trámite. */
public record CreatedServiceRequestResponse(String code, String type, Instant createdAt) {}
