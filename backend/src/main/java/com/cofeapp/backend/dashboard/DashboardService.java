package com.cofeapp.backend.dashboard;

import com.cofeapp.backend.common.exception.BadRequestException;
import com.cofeapp.backend.common.exception.NotFoundException;
import com.cofeapp.backend.dashboard.dto.DashboardSummaryResponse;
import com.cofeapp.backend.dashboard.dto.TopProductResponse;
import com.cofeapp.backend.domain.Order;
import com.cofeapp.backend.domain.OrderItem;
import com.cofeapp.backend.domain.OrderPayment;
import com.cofeapp.backend.domain.enums.OrderStatus;
import com.cofeapp.backend.domain.enums.PaymentMethod;
import com.cofeapp.backend.location.LocationRepository;
import com.cofeapp.backend.order.OrderItemRepository;
import com.cofeapp.backend.order.OrderPaymentRepository;
import com.cofeapp.backend.order.OrderRepository;
import com.cofeapp.backend.stock.StockService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Bloque E — dashboard de KPIs para gerencia (ítems "facturación/
 * estadísticas/KPI"). Reservado a OWNER/ADMIN/SUPERVISOR (ver {@code
 * DashboardController}), a diferencia de las pantallas de solo-lectura de
 * Productos/Insumos, que son abiertas a todos.
 *
 * <p>Decisiones de alcance confirmadas por Fran antes de escribir el
 * código: (1) KPIs elegidos — ventas del período, productos más vendidos,
 * ventas por método de pago, alertas de stock bajo; (2) el dashboard es
 * POR LOCAL, con selector, no un consolidado de todo el negocio (el
 * frontend arma el selector, este endpoint siempre pide un
 * {@code locationId}); (3) solo gerencia lo ve.
 *
 * <p><b>Qué cuenta como "venta"</b>: pedidos en estado {@code DELIVERED}
 * cuyo {@code closedAt} cae dentro del rango {@code [from, to]} pedido —
 * NUNCA {@code createdAt} (un pedido cuenta como venta en el momento en
 * que se completó, no en el que se cargó) y nunca pedidos {@code
 * CANCELLED} o todavía abiertos (no son ventas reales, "cerrado en el
 * período" es el criterio que evita contar dos veces un pedido que se creó
 * un día y se entregó al siguiente, aunque en un café esto casi nunca pasa
 * en la práctica). {@code totalSales}/{@code orderCount}/{@code
 * averageTicket} salen directo de esos pedidos.
 *
 * <p><b>{@code topProducts}</b>: se arma agrupando por {@code productId}
 * los {@code OrderItem} de esos mismos pedidos, sumando cantidad e
 * ingresos, ordenado de mayor a menor cantidad vendida — top 5 fijo (no
 * hace falta configurarlo, es un vistazo rápido, no un reporte).
 *
 * <p><b>{@code salesByMethod}</b>: {@code Order} NO tiene método de pago
 * propio (ver el Javadoc de {@code OrderPayment}) — se arma agrupando por
 * {@code paymentMethod} los {@code OrderPayment} de esos mismos pedidos.
 * Puede no coincidir exactamente con {@code totalSales} si un pedido
 * quedó cobrado de más/menos por algún motivo excepcional; en el uso
 * normal (pedido cobrado por completo antes de poder marcarse DELIVERED,
 * ver {@code OrderService#assertFullyPaid}) van a coincidir siempre.
 *
 * <p><b>{@code lowIngredients}/{@code lowProducts}</b>: reutiliza tal cual
 * {@link StockService#ingredientLevels}/{@link StockService#productLevels}
 * del Bloque D, filtrando a los que ya vienen marcados {@code
 * belowMinimum} — no se duplica ningún cálculo de stock acá.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

  private static final int TOP_PRODUCTS_LIMIT = 5;

  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final OrderPaymentRepository orderPaymentRepository;
  private final LocationRepository locationRepository;
  private final StockService stockService;

  public DashboardSummaryResponse summary(String locationId, Instant from, Instant to) {
    assertLocationExists(locationId);
    if (!from.isBefore(to)) {
      throw new BadRequestException("\"from\" tiene que ser anterior a \"to\".");
    }

    List<Order> orders =
        orderRepository.findAllByLocationIdAndStatusAndClosedAtBetween(locationId, OrderStatus.DELIVERED, from, to);
    List<String> orderIds = orders.stream().map(Order::getId).toList();

    BigDecimal totalSales = orders.stream().map(Order::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    long orderCount = orders.size();
    BigDecimal averageTicket =
        orderCount == 0 ? BigDecimal.ZERO : totalSales.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);

    List<TopProductResponse> topProducts = orderIds.isEmpty() ? List.of() : topProducts(orderIds);
    Map<PaymentMethod, BigDecimal> salesByMethod =
        orderIds.isEmpty() ? new EnumMap<>(PaymentMethod.class) : salesByMethod(orderIds);

    return new DashboardSummaryResponse(
        locationId,
        from,
        to,
        totalSales,
        orderCount,
        averageTicket,
        topProducts,
        salesByMethod,
        stockService.ingredientLevels(locationId).stream().filter(level -> level.belowMinimum()).toList(),
        stockService.productLevels(locationId).stream().filter(level -> level.belowMinimum()).toList());
  }

  /** Top 5 por cantidad vendida — ver el Javadoc de la clase. */
  private List<TopProductResponse> topProducts(List<String> orderIds) {
    Map<String, ProductAccumulator> byProduct = new LinkedHashMap<>();
    for (OrderItem item : orderItemRepository.findAllByOrderIdIn(orderIds)) {
      byProduct
          .computeIfAbsent(item.getProductId(), id -> new ProductAccumulator(item.getProductName()))
          .add(item.getQuantity(), item.getSubtotal());
    }
    return byProduct.entrySet().stream()
        .map(entry -> new TopProductResponse(entry.getKey(), entry.getValue().productName, entry.getValue().quantity, entry.getValue().revenue))
        .sorted(Comparator.comparingLong(TopProductResponse::quantity).reversed())
        .limit(TOP_PRODUCTS_LIMIT)
        .toList();
  }

  private Map<PaymentMethod, BigDecimal> salesByMethod(List<String> orderIds) {
    Map<PaymentMethod, BigDecimal> totals = new EnumMap<>(PaymentMethod.class);
    for (OrderPayment payment : orderPaymentRepository.findAllByOrderIdIn(orderIds)) {
      totals.merge(payment.getPaymentMethod(), payment.getAmount(), BigDecimal::add);
    }
    return totals;
  }

  private void assertLocationExists(String locationId) {
    locationRepository
        .findByIdScoped(locationId)
        .orElseThrow(() -> new NotFoundException("No se encontró el local " + locationId + "."));
  }

  /** Acumulador mutable de cantidad/ingresos por producto — ver {@link #topProducts}. */
  private static final class ProductAccumulator {
    private final String productName;
    private long quantity;
    private BigDecimal revenue = BigDecimal.ZERO;

    private ProductAccumulator(String productName) {
      this.productName = productName;
    }

    private void add(int itemQuantity, BigDecimal itemSubtotal) {
      quantity += itemQuantity;
      revenue = revenue.add(itemSubtotal);
    }
  }
}
