package com.cofeapp.backend.order;

import com.cofeapp.backend.domain.Order;
import com.cofeapp.backend.domain.enums.OrderStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, String> {

  /**
   * Reemplaza a {@code findById()} (bloqueado por {@code
   * TenantSafeRepositoryImpl} para esta entidad tenant-scoped).
   */
  @Query("select o from Order o where o.id = :id")
  Optional<Order> findByIdScoped(@Param("id") String id);

  /**
   * Igual que {@link #findByIdScoped} pero bloqueando la fila (SELECT ...
   * FOR UPDATE) hasta que termina la operación: dos cobros, agregados,
   * descuentos o cambios de estado sobre el MISMO pedido se hacen de a uno
   * (prueba de carga, oct. 2026: sin esto, dos "Cobrar" a la vez cobraban
   * dos veces).
   */
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Order o where o.id = :id")
  Optional<Order> findByIdScopedForUpdate(@Param("id") String id);

  /**
   * Único punto de lectura para la idempotencia de {@code
   * OrderService#create} — al ser un método derivado (JPQL por debajo)
   * respeta el {@code @Filter} activo, así que nunca puede devolver el
   * pedido de OTRO negocio aunque alguien mande el mismo idempotencyKey
   * por accidente.
   */
  Optional<Order> findByIdempotencyKey(String idempotencyKey);

  /** Para la regla "una mesa, un pedido abierto a la vez" — ver {@code OrderService#assertTableFree}. */
  boolean existsByTableIdAndStatusIn(String tableId, Collection<OrderStatus> statuses);

  List<Order> findAllByOrderByCreatedAtDesc();

  List<Order> findAllByLocationIdOrderByCreatedAtDesc(String locationId);

  /** Respaldo por polling para el KDS — ver {@code OrderService#findActive}. */
  List<Order> findAllByStatusInOrderByCreatedAtAsc(Collection<OrderStatus> statuses);

  /** Misma idea que la de arriba, pero acotada a un local — ver {@code OrderService#findActiveByLocation}. */
  List<Order> findAllByLocationIdAndStatusInOrderByCreatedAtAsc(String locationId, Collection<OrderStatus> statuses);

  /**
   * Bloque E (dashboard) — pedidos ya ENTREGADOS de un local cuyo {@code
   * closedAt} cae en el rango pedido. Se filtra por {@code closedAt} (el
   * momento en que la venta quedó completa), no por {@code createdAt} — ver
   * el Javadoc de {@code DashboardService#summary}.
   */
  List<Order> findAllByLocationIdAndStatusAndClosedAtBetween(
      String locationId, OrderStatus status, Instant from, Instant to);

  /** Reportes (paso 2.6) — lo mismo, para todos los locales del negocio. */
  List<Order> findAllByStatusAndClosedAtBetween(OrderStatus status, Instant from, Instant to);

  /** V19 — ¿ya se cargó ese número de pedido de esa app? */
  boolean existsByChannelIdAndChannelOrderRefIgnoreCase(String channelId, String channelOrderRef);
}
