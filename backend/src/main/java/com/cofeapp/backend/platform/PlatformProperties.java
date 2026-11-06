package com.cofeapp.backend.platform;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.platform.*} (V15) — la cuenta de superadmin y el contacto que
 * ve un café suspendido.
 *
 * @param adminEmail / adminPassword: la cuenta de superadmin se crea (o se
 *     le actualiza la contraseña) al arrancar. Vacíos = no hay superadmin.
 * @param supportContact: a quién comunicarse si el café está suspendido.
 */
@ConfigurationProperties(prefix = "app.platform")
public record PlatformProperties(String adminEmail, String adminPassword, String supportContact) {}
