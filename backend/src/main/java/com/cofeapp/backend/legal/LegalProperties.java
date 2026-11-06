package com.cofeapp.backend.legal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Términos y política de privacidad (V17 — ver README, paso 3.3).
 *
 * @param version la versión vigente de los documentos (la fecha desde la que
 *     rigen, ej. "2026-10-01"). Tiene que coincidir con la de
 *     {@code frontend/src/app/features/legal/legal-info.ts}. Si se cambia, a
 *     cada dueño le aparece para aceptarla de nuevo al entrar.
 */
@ConfigurationProperties(prefix = "app.legal")
public record LegalProperties(String version) {

  public LegalProperties {
    if (version == null || version.isBlank()) version = "2026-10-01";
  }
}
