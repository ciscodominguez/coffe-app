package com.cofeapp.backend.order;

import com.cofeapp.backend.order.dto.OrderEventMessage;
import com.cofeapp.backend.order.dto.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publica al tópico STOMP del local del pedido (ver {@link
 * OrderTopics}), que es el mismo "room" al que {@link
 * com.cofeapp.backend.common.security.WebSocketAuthHandshakeInterceptor}
 * suscribió al socket en el handshake.
 *
 * <p>Publica recién después de que el commit de la transacción en curso
 * termine bien ({@link #publishAfterCommit}), nunca antes — así un rollback
 * (por ejemplo, una excepción tardía dentro del mismo {@code @Transactional}
 * de {@link OrderService}) nunca deja al KDS con un pedido "fantasma" que
 * después no existe en la base. Si no hay una transacción activa (caso que
 * no ocurre en producción, donde {@link OrderService#create}, {@link
 * OrderService#updateStatus} y {@link OrderService#addPayment} son siempre
 * {@code @Transactional}, pero sí
 * podría pasar en un test unitario que invoque este bean directo) publica
 * de inmediato, para no perder el evento silenciosamente.
 */
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

  private final SimpMessagingTemplate messagingTemplate;

  public void orderCreated(OrderResponse order) {
    publishAfterCommit(order, "order:created");
  }

  public void orderStatusChanged(OrderResponse order) {
    publishAfterCommit(order, "order:status_changed");
  }

  /**
   * Cambios del pedido que no son ni alta ni transición de estado — hoy,
   * solo un cobro nuevo ({@link OrderService#addPayment}): el pedido viaja
   * completo, con {@code payments}/{@code paidTotal}/{@code balanceDue} ya
   * actualizados, así cualquier otra pantalla abierta del mismo local (por
   * ejemplo, otro mesero mirando el KDS) habilita "Marcar entregado" al
   * toque, sin esperar al polling de respaldo.
   */
  public void orderUpdated(OrderResponse order) {
    publishAfterCommit(order, "order:updated");
  }

  private void publishAfterCommit(OrderResponse order, String event) {
    Runnable publish =
        () ->
            messagingTemplate.convertAndSend(
                OrderTopics.locationTopic(order.locationId()), new OrderEventMessage(event, order));

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              publish.run();
            }
          });
    } else {
      publish.run();
    }
  }
}
