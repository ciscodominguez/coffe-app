package com.cofeapp.backend.support.dto;

/**
 * Cómo contactar a soporte (V18). Público: el login también lo usa, y la
 * app lo guarda para tenerlo aunque después el servidor no responda.
 *
 * @param whatsapp número para wa.me (solo números, con código de país) o null
 * @param contact texto libre de SUPPORT_CONTACT (ej. "Fran por WhatsApp al 223 555-0000") o null
 */
public record SupportInfoResponse(String whatsapp, String contact) {}
