/**
 * Tipos de autenticación y roles, espejo de los del backend. El frontend
 * Angular no depende de ningún paquete del backend: este archivo es el
 * único lugar de verdad para lo que consume el frontend, sin tener que
 * importar nada del backend. Si se agrega un rol nuevo en el backend, hay
 * que agregarlo acá también — no hay chequeo automático que lo garantice.
 */

export type UserRole = 'OWNER' | 'ADMIN' | 'SUPERVISOR' | 'WAITER' | 'KITCHEN';

export const USER_ROLE_LABELS: Readonly<Record<UserRole, string>> = {
  OWNER: 'Dueño/a',
  ADMIN: 'Administrador/a',
  SUPERVISOR: 'Supervisor/a',
  WAITER: 'Mesera/o',
  KITCHEN: 'Cocina/Barra',
};

/**
 * V15 — la cuenta de Fran (panel de superadmin). No es un rol de ningún
 * café: no aparece en USER_ROLE_LABELS ni se puede asignar a un empleado.
 */
export const SUPERADMIN_ROLE = 'SUPERADMIN';

export interface AuthUserDto {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  /** Nulo para el superadmin (no pertenece a ningún café). */
  businessId: string | null;
  /** Nulo si el usuario tiene acceso a todos los locales del negocio (típicamente OWNER/ADMIN). */
  locationId: string | null;
}

export interface AuthResponse {
  accessToken: string;
  user: AuthUserDto;
}

export interface LoginInput {
  email: string;
  password: string;
}

export interface RegisterBusinessInput {
  legalName: string;
  tradeName?: string;
  taxId: string;
  fiscalCondition: 'MONOTRIBUTO' | 'RESPONSABLE_INSCRIPTO' | 'EXENTO';
  ownerEmail: string;
  ownerPassword: string;
  ownerFirstName: string;
  ownerLastName: string;
  /** V17 — la casilla de términos y privacidad. */
  acceptTerms: boolean;
}
