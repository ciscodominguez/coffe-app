package com.cofeapp.backend.order;

import com.cofeapp.backend.common.security.WebSocketAuthHandshakeInterceptor;
import java.util.Map;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * El cliente nunca debería poder elegir de qué local recibe eventos. Pero
 * en STOMP es el cliente el que manda un frame SUBSCRIBE con el destino
 * que quiera, así que sin este interceptor
 * un socket ya autenticado (pero para el local A) podría suscribirse al
 * tópico del local B con solo conocer su id.
 *
 * <p>Este {@link ChannelInterceptor}, registrado en el canal de entrada
 * (ver {@code WebSocketConfig#configureClientInboundChannel}), es lo que
 * recupera esa garantía: solo deja pasar un SUBSCRIBE cuyo destino sea
 * EXACTAMENTE el tópico del {@code locationId} que quedó resuelto en el
 * handshake (nunca uno a mano por el cliente) — ver {@link
 * WebSocketAuthHandshakeInterceptor}. Cualquier otro intento tira una
 * {@link MessagingException}, que Spring traduce en un frame STOMP ERROR de
 * vuelta al cliente y cierra la conexión.
 */
@Component
public class OrderSubscriptionInterceptor implements ChannelInterceptor {

  @Override
  public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
    if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
      return message;
    }

    Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
    String locationId =
        sessionAttributes == null
            ? null
            : (String) sessionAttributes.get(WebSocketAuthHandshakeInterceptor.SESSION_ATTR_LOCATION_ID);

    if (locationId == null || !OrderTopics.locationTopic(locationId).equals(accessor.getDestination())) {
      throw new MessagingException("No podés suscribirte a ese tópico.");
    }

    return message;
  }
}
