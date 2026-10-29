package com.cofeapp.backend.order;

/**
 * Único punto donde se arma el nombre del tópico STOMP por local — lo usan
 * {@link OrderEventPublisher} (para publicar) y {@link
 * OrderSubscriptionInterceptor} (para validar a qué se puede suscribir un
 * socket ya autenticado), así nunca pueden desincronizarse.
 *
 * <p>El cliente STOMP se suscribe él mismo a este destino; lo que
 * garantiza que el server (y no el cliente) decide el local es que {@link
 * OrderSubscriptionInterceptor} solo deja pasar una suscripción a
 * exactamente el tópico del local resuelto en el handshake (ver {@link
 * com.cofeapp.backend.common.security.WebSocketAuthHandshakeInterceptor}).
 */
public final class OrderTopics {

  private OrderTopics() {}

  public static String locationTopic(String locationId) {
    return "/topic/locations/" + locationId + "/orders";
  }
}
