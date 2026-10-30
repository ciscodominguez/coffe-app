export const environment = {
  production: false,
  // Incluye el prefijo /api/v1.
  apiBaseUrl: 'http://localhost:3000/api/v1',
  // Mismo host/puerto/prefijo que apiBaseUrl, esquema ws:// en vez de
  // http:// — lo usa OrdersRealtimeService para el gateway de WebSocket
  // (ver WebSocketConfig del backend: el endpoint STOMP cuelga del mismo
  // context-path /api/v1 que el resto de la API).
  wsBaseUrl: 'ws://localhost:3000/api/v1',
};
