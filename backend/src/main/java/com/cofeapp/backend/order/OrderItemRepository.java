package com.cofeapp.backend.order;

import com.cofeapp.backend.domain.OrderItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, String> {

  /**
   * Reemplaza a {@code findById()} (bloqueado por {@code
   * TenantSafeRepositoryImpl} para esta entidad tenant-scoped).
   */
  @Query("select i from OrderItem i where i.id = :id")
  Optional<OrderItem> findByIdScoped(@Param("id") String id);

  /** En el orden en que se cargaron (V13: por renglón; los pedidos viejos, con renglón 0, por fecha). */
  List<OrderItem> findAllByOrderIdOrderByLineNumberAscCreatedAtAsc(String orderId);

  /** Bloque E (dashboard) — todos los ítems de un lote de pedidos, para armar el ranking de productos más vendidos de un período. */
  List<OrderItem> findAllByOrderIdIn(Collection<String> orderIds);
}
