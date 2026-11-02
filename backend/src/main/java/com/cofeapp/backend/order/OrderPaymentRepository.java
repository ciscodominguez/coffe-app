package com.cofeapp.backend.order;

import com.cofeapp.backend.domain.OrderPayment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderPaymentRepository extends JpaRepository<OrderPayment, String> {

  List<OrderPayment> findAllByOrderIdOrderByCreatedAtAsc(String orderId);

  /** Para totalizar las ventas por método de un turno de caja — ver {@code CashSessionService}. */
  List<OrderPayment> findAllByCashSessionIdOrderByCreatedAtAsc(String cashSessionId);

  /** Bloque E (dashboard) — todos los cobros de un lote de pedidos, para armar el desglose por método de pago de un período. */
  List<OrderPayment> findAllByOrderIdIn(Collection<String> orderIds);
}
