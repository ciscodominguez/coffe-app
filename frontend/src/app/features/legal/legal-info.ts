/**
 * Datos de los papeles (paso 3.3): Términos y condiciones y Política de
 * privacidad. COMPLETALOS antes de publicar la app (idealmente con un
 * abogado). Lo que dice "[COMPLETAR…]" se ve resaltado en las páginas
 * para que no se te pase.
 *
 * Si cambiás el TEXTO de los documentos (terms.component.html /
 * privacy.component.html) de forma importante, subí la versión acá Y en el
 * backend (app.legal.version en application.yml, o LEGAL_VERSION en el
 * .env): a cada dueño le va a aparecer para aceptarla de nuevo.
 */
export const LEGAL_INFO = {
  /** Tiene que ser igual a app.legal.version del backend. */
  version: '2026-10-01',
  /** Cómo se muestra la fecha de la versión. */
  versionLabel: '1 de octubre de 2026',

  /** Quién presta el servicio: tu nombre y apellido (o la razón social, si armás una sociedad). */
  providerName: '[COMPLETAR: nombre y apellido o razón social]',
  providerTaxId: '[COMPLETAR: CUIT]',
  providerAddress: '[COMPLETAR: domicilio]',
  /** Email para consultas, reclamos y derechos sobre datos personales. */
  contactEmail: '[COMPLETAR: email de contacto]',
  /** Ciudad de los tribunales que corresponden (ej. Mar del Plata). */
  jurisdiction: '[COMPLETAR: ciudad de los tribunales]',
  /** Dónde está alojada la app (proveedor y país), cuando la publiques. */
  hosting: '[COMPLETAR: proveedor de hosting y país]',
} as const;

export function isTodo(value: string): boolean {
  return value.startsWith('[COMPLETAR');
}
