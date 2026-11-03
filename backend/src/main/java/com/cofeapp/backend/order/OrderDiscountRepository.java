package com.cofeapp.backend.order;

import com.cofeapp.backend.domain.OrderDiscount;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderDiscountRepository extends JpaRepository<OrderDiscount, String> {

  List<OrderDiscount> findAllByOrderIdOrderByCreatedAtAsc(String orderId);

  /** Reportes (paso 2.6) — los descuentos de un lote de pedidos. */
  List<OrderDiscount> findAllByOrderIdIn(java.util.Collection<String> orderIds);
}
