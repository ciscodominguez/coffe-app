package com.cofeapp.backend.domain.enums;

/**
 * Roles RBAC.
 *
 * SUPERADMIN de plataforma (panel superadmin, Etapa 5) se modelará como una
 * tabla/rol aparte sin business_id — por diseño, todo {@link
 * com.cofeapp.backend.domain.User} de esta tabla pertenece siempre a un
 * negocio.
 */
public enum UserRole {
  OWNER,
  ADMIN,
  SUPERVISOR,
  WAITER,
  KITCHEN,
}
