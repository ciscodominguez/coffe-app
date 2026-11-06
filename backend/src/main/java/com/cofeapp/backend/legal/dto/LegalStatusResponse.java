package com.cofeapp.backend.legal.dto;

import java.time.Instant;

/**
 * Si el café aceptó la versión vigente de los términos (V17).
 *
 * @param mustAccept true si el dueño tiene que aceptarlos (nunca los aceptó, o hay una versión nueva)
 */
public record LegalStatusResponse(String currentVersion, String acceptedVersion, Instant acceptedAt, boolean mustAccept) {}
