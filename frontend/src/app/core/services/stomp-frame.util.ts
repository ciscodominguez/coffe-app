/**
 * Frames STOMP mínimos, armados/parseados a mano — sin depender de una
 * librería externa (@stomp/stompjs). Este proyecto solo necesita CONNECT y
 * SUBSCRIBE de un único tópico de solo lectura (ver OrdersRealtimeService),
 * así que alcanza con esto. Misma lógica que ya se probó a mano end-to-end
 * contra el backend real (ver orders-ws-tester.html, usado para validar el
 * gateway de WebSocket antes de escribir este service).
 */

export interface StompFrame {
  command: string;
  headers: Record<string, string>;
  body: string;
}

export function buildStompFrame(command: string, headers: Record<string, string>, body = ''): string {
  let frame = command + '\n';
  for (const key of Object.keys(headers)) frame += `${key}:${headers[key]}\n`;
  frame += '\n' + body + '\0';
  return frame;
}

/** Devuelve null para un heart-beat (frame vacío) — nunca para un frame real. */
export function parseStompFrame(raw: string): StompFrame | null {
  const text = raw.replace(/\r\n/g, '\n').replace(/\0+$/, '');
  if (text.trim() === '') return null;

  const lines = text.split('\n');
  const command = lines[0];
  const headers: Record<string, string> = {};
  let i = 1;
  for (; i < lines.length; i++) {
    if (lines[i] === '') {
      i++;
      break;
    }
    const idx = lines[i].indexOf(':');
    if (idx > -1) headers[lines[i].slice(0, idx)] = lines[i].slice(idx + 1);
  }
  const body = lines.slice(i).join('\n');
  return { command, headers, body };
}
