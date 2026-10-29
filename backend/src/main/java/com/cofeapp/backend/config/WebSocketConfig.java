package com.cofeapp.backend.config;

import com.cofeapp.backend.common.security.WebSocketAuthHandshakeInterceptor;
import com.cofeapp.backend.order.OrderSubscriptionInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Tiempo real de pedidos (un canal por local) con STOMP sobre WebSocket —
 * la pieza nativa/estándar de Spring para este caso.
 *
 * <p>Piezas:
 *
 * <ul>
 *   <li>Endpoint {@code /ws/orders}
 *       (efectivamente {@code /api/v1/ws/orders}, bajo el mismo
 *       context-path que el resto de la API).
 *   <li>Autenticación del handshake (JWT en el query param {@code token}):
 *       {@link WebSocketAuthHandshakeInterceptor}, que valida el token y
 *       resuelve el local ANTES de aceptar el handshake.
 *   <li>Un tópico por local, {@code
 *       /topic/locations/{locationId}/orders} (ver {@code
 *       com.cofeapp.backend.order.OrderTopics}), con {@link
 *       OrderSubscriptionInterceptor} garantizando que un socket solo
 *       pueda suscribirse al tópico de SU local resuelto en el handshake
 *       (el server decide el local, el cliente no elige).
 *   <li>Publicación: {@code com.cofeapp.backend.order.OrderEventPublisher},
 *       que publica a ese tópico vía {@code SimpMessagingTemplate}.
 * </ul>
 *
 * <p>Sin SockJS: un cliente STOMP moderno (p. ej. {@code @stomp/stompjs} en
 * el frontend Angular) habla WebSocket nativo sin falta del fallback
 * long-polling que SockJS existe para resolver — innecesario en 2026.
 *
 * <p>No hace falta {@code setApplicationDestinationPrefixes("/app")}
 * porque, a diferencia de un chat, acá el cliente nunca le manda mensajes
 * de negocio al servidor por este canal — crear un pedido o cambiar su
 * estado sigue siendo, a propósito, un {@code POST}/{@code PATCH} REST
 * normal (ver {@code OrderController}); WebSocket acá es un canal de
 * salida (server -> cliente) únicamente.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final WebSocketAuthHandshakeInterceptor webSocketAuthHandshakeInterceptor;
  private final OrderSubscriptionInterceptor orderSubscriptionInterceptor;

  /** Mismo {@code CORS_ORIGIN} que ya usa {@code SecurityConfig} para la API REST. */
  @Value("${app.cors.origin}")
  private String corsOrigin;

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint("/ws/orders")
        .setAllowedOriginPatterns(corsOrigin)
        .addInterceptors(webSocketAuthHandshakeInterceptor);
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(orderSubscriptionInterceptor);
  }
}
