/**
 * Configuración de producción/build por defecto. Angular resuelve este
 * archivo en tiempo de build vía fileReplacements en angular.json (no lee
 * variables de entorno del navegador).
 *
 * En el servidor (ver docs/SERVIDOR.md) la app y la API salen del MISMO
 * dominio: Caddy sirve la app y le pasa /api/ al backend. Por eso las
 * direcciones son relativas y no hay que cambiar nada al elegir el dominio.
 */
const wsScheme = typeof location !== 'undefined' && location.protocol === 'http:' ? 'ws' : 'wss';
const host = typeof location !== 'undefined' ? location.host : '';

export const environment = {
  production: true,
  apiBaseUrl: '/api/v1',
  // wss:// (WebSocket sobre HTTPS) al mismo dominio; ws:// solo en la copia
  // de prueba local, que va por http://localhost.
  wsBaseUrl: `${wsScheme}://${host}/api/v1`,
};
