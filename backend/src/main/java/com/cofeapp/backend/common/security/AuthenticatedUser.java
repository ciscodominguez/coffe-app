package com.cofeapp.backend.common.security;

/**
 * Forma del payload firmado en el JWT de sesión.
 *
 * <p>{@code businessId} y {@code locationId} viajan DENTRO del token (nunca
 * como parámetro de la request) precisamente para que ningún cliente pueda
 * "elegir" a qué tenant consultar.
 *
 * <p>Es el objeto que Spring Security guarda como principal de la
 * {@code Authentication} — se obtiene en un controller con
 * {@code @AuthenticationPrincipal AuthenticatedUser user}.
 *
 * @param userId subject del token (id del User).
 * @param businessId tenant del usuario.
 * @param locationId nulo si el usuario tiene acceso a todos los locales del negocio.
 * @param role uno de los 5 valores de {@link com.cofeapp.backend.domain.enums.UserRole}.
 * @param email email del usuario (informativo — la fuente de verdad de identidad es userId).
 */
public record AuthenticatedUser(String userId, String businessId, String locationId, String role, String email) {}
