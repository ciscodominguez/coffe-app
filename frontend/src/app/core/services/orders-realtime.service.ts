import { Injectable, OnDestroy, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { environment } from '../../../environments/environment';
import { OrderEventMessage } from '../models/order.model';
import { AuthService } from './auth.service';
import { buildStompFrame, parseStompFrame } from './stomp-frame.util';

export type RealtimeStatus = 'disconnected' | 'connecting' | 'connected' | 'reconnecting';

const RECONNECT_DELAY_MS = 4000;

/**
 * Conexión en tiempo real a los eventos de pedidos: habla STOMP sobre
 * WebSocket (ver stomp-frame.util.ts) contra el {@code WebSocketConfig}
 * del backend Spring.
 *
 * Deliberadamente NO {@code providedIn: 'root'}: se registra como
 * `providers: [OrdersRealtimeService]` en el propio componente de KDS, así
 * Angular llama a {@link #ngOnDestroy} solo (cierra la conexión sola) en
 * cuanto ese componente se destruye — sin depender de que el componente se
 * acuerde de llamar a {@link #disconnect} a mano.
 *
 * Reconecta solo ante un cierre INESPERADO (nunca tras un {@link
 * #disconnect} manual) — hace falta porque hoy no hay heart-beats
 * configurados de ningún lado (ver el Javadoc de {@code WebSocketConfig}
 * del backend), así que una conexión inactiva puede caerse sola sin
 * aviso; mientras tanto, el componente de KDS mantiene un polling de
 * respaldo (mismo criterio que {@code GET /orders/active} en el backend)
 * para no depender 100% de que el reintento sea instantáneo.
 */
@Injectable()
export class OrdersRealtimeService implements OnDestroy {
  private ws: WebSocket | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private currentLocationId: string | null = null;
  private manuallyClosed = false;
  private subscriptionSeq = 0;

  private readonly eventsSubject = new Subject<OrderEventMessage>();
  readonly events$ = this.eventsSubject.asObservable();

  readonly status = signal<RealtimeStatus>('disconnected');

  constructor(private readonly authService: AuthService) {}

  connect(locationId: string): void {
    this.manuallyClosed = false;
    this.currentLocationId = locationId;
    this.open();
  }

  disconnect(): void {
    this.manuallyClosed = true;
    this.currentLocationId = null;
    this.clearReconnectTimer();
    this.status.set('disconnected');
    this.ws?.close(1000, 'Cierre manual');
    this.ws = null;
  }

  ngOnDestroy(): void {
    this.disconnect();
    this.eventsSubject.complete();
  }

  private open(): void {
    const token = this.authService.getAccessToken();
    const locationId = this.currentLocationId;
    if (!token || !locationId) return;

    this.status.set(this.status() === 'disconnected' ? 'connecting' : 'reconnecting');

    const url = `${environment.wsBaseUrl}/ws/orders?token=${encodeURIComponent(token)}`;
    const ws = new WebSocket(url);
    this.ws = ws;

    ws.onopen = () => {
      ws.send(buildStompFrame('CONNECT', { 'accept-version': '1.1,1.2', 'heart-beat': '0,0' }));
    };

    ws.onmessage = (evt: MessageEvent<string>) => {
      const frame = parseStompFrame(evt.data);
      if (!frame) return; // heart-beat

      if (frame.command === 'CONNECTED') {
        this.status.set('connected');
        this.subscriptionSeq++;
        ws.send(
          buildStompFrame('SUBSCRIBE', {
            id: `sub-${this.subscriptionSeq}`,
            destination: `/topic/locations/${locationId}/orders`,
          }),
        );
      } else if (frame.command === 'MESSAGE') {
        try {
          this.eventsSubject.next(JSON.parse(frame.body) as OrderEventMessage);
        } catch {
          // No debería pasar nunca en producción — OrderEventMessage
          // siempre viaja como JSON (ver OrderEventPublisher del backend).
        }
      }
      // Un frame ERROR del backend viene siempre seguido de un cierre de
      // conexión — el manejo real pasa por onclose, más abajo, no hace
      // falta duplicar lógica acá.
    };

    ws.onclose = () => {
      this.ws = null;
      if (this.manuallyClosed) return;
      this.status.set('reconnecting');
      this.scheduleReconnect();
    };

    ws.onerror = () => {
      // onclose siempre se dispara después de onerror en un WebSocket del browser.
    };
  }

  private scheduleReconnect(): void {
    this.clearReconnectTimer();
    this.reconnectTimer = setTimeout(() => {
      if (!this.manuallyClosed && this.currentLocationId) this.open();
    }, RECONNECT_DELAY_MS);
  }

  private clearReconnectTimer(): void {
    if (this.reconnectTimer !== null) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }
}
