package com.cofeapp.backend.report;

import com.cofeapp.backend.business.BusinessRepository;
import com.cofeapp.backend.cash.CashSessionService;
import com.cofeapp.backend.cash.dto.CashSessionResponse;
import com.cofeapp.backend.common.context.TenantContext;
import com.cofeapp.backend.common.excel.XlsxWriter;
import com.cofeapp.backend.common.excel.XlsxWriter.Cell;
import com.cofeapp.backend.common.exception.BadRequestException;
import com.cofeapp.backend.common.exception.NotFoundException;
import com.cofeapp.backend.domain.Location;
import com.cofeapp.backend.domain.Order;
import com.cofeapp.backend.domain.OrderDiscount;
import com.cofeapp.backend.domain.OrderItem;
import com.cofeapp.backend.domain.OrderPayment;
import com.cofeapp.backend.domain.RestaurantTable;
import com.cofeapp.backend.domain.User;
import com.cofeapp.backend.domain.enums.CashSessionStatus;
import com.cofeapp.backend.domain.enums.DiscountType;
import com.cofeapp.backend.domain.enums.OrderStatus;
import com.cofeapp.backend.domain.enums.PaymentMethod;
import com.cofeapp.backend.location.LocationRepository;
import com.cofeapp.backend.order.OrderDiscountRepository;
import com.cofeapp.backend.order.OrderItemRepository;
import com.cofeapp.backend.order.OrderPaymentRepository;
import com.cofeapp.backend.order.OrderRepository;
import com.cofeapp.backend.table.TableRepository;
import com.cofeapp.backend.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reporte en Excel para el contador (hoja de ruta, paso 2.6). Un solo
 * archivo con cinco hojas:
 *
 * <ol>
 *   <li><b>Resumen</b>: período, local, ventas, pedidos, ticket promedio,
 *       descuentos y ventas por método de pago.
 *   <li><b>Ventas</b>: una fila por pedido entregado, con lo cobrado por
 *       cada método.
 *   <li><b>Productos</b>: unidades y plata de cada producto.
 *   <li><b>Cierres de caja</b>: una fila por turno cerrado, con esperado,
 *       contado y diferencia.
 *   <li><b>Descuentos</b>: cada descuento aplicado, quién y por qué.
 * </ol>
 *
 * <p>Mismo criterio que el panel de Inicio ({@code DashboardService}): una
 * venta cuenta cuando el pedido se ENTREGA (cerrado y cobrado), por eso se
 * filtra por {@code closedAt}. Las fechas se muestran en la hora del café
 * ({@code zone}, por defecto la de Argentina).
 */
@Service
@RequiredArgsConstructor
public class ReportService {

  public static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

  private static final List<PaymentMethod> METHODS =
      List.of(
          PaymentMethod.CASH,
          PaymentMethod.DEBIT_CARD,
          PaymentMethod.CREDIT_CARD,
          PaymentMethod.TRANSFER,
          PaymentMethod.QR,
          PaymentMethod.OTHER,
          PaymentMethod.APP);

  private static final Map<PaymentMethod, String> METHOD_LABELS =
      Map.of(
          PaymentMethod.CASH, "Efectivo",
          PaymentMethod.DEBIT_CARD, "Débito",
          PaymentMethod.CREDIT_CARD, "Crédito",
          PaymentMethod.TRANSFER, "Transferencia",
          PaymentMethod.QR, "QR",
          PaymentMethod.OTHER, "Otro",
          PaymentMethod.APP, "App de delivery");

  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final OrderPaymentRepository orderPaymentRepository;
  private final OrderDiscountRepository orderDiscountRepository;
  private final LocationRepository locationRepository;
  private final TableRepository tableRepository;
  private final UserRepository userRepository;
  private final BusinessRepository businessRepository;
  private final CashSessionService cashSessionService;
  private final TenantContext tenantContext;

  /** Qué se descarga: el archivo y un nombre sugerido. */
  public record ExcelReport(byte[] content, String fileName) {}

  public static ZoneId parseZone(String zone) {
    if (zone == null || zone.isBlank()) return DEFAULT_ZONE;
    try {
      return ZoneId.of(zone);
    } catch (DateTimeException e) {
      throw new BadRequestException("Zona horaria desconocida: " + zone + ".");
    }
  }

  @Transactional(readOnly = true)
  public ExcelReport excel(String locationId, Instant from, Instant to, ZoneId zone) {
    if (!from.isBefore(to)) {
      throw new BadRequestException("La fecha \"desde\" tiene que ser anterior a \"hasta\".");
    }
    Map<String, Location> locations = new LinkedHashMap<>();
    locationRepository.findAllByOrderByCreatedAtAsc().forEach(location -> locations.put(location.getId(), location));
    if (locationId != null && !locations.containsKey(locationId)) {
      throw new NotFoundException("No se encontró el local " + locationId + ".");
    }

    List<Order> orders =
        (locationId != null
                ? orderRepository.findAllByLocationIdAndStatusAndClosedAtBetween(locationId, OrderStatus.DELIVERED, from, to)
                : orderRepository.findAllByStatusAndClosedAtBetween(OrderStatus.DELIVERED, from, to))
            .stream()
            .sorted(Comparator.comparing(Order::getClosedAt))
            .toList();
    List<String> orderIds = orders.stream().map(Order::getId).toList();
    List<OrderItem> items = orderIds.isEmpty() ? List.of() : orderItemRepository.findAllByOrderIdIn(orderIds);
    List<OrderPayment> payments = orderIds.isEmpty() ? List.of() : orderPaymentRepository.findAllByOrderIdIn(orderIds);
    List<OrderDiscount> discounts = orderIds.isEmpty() ? List.of() : orderDiscountRepository.findAllByOrderIdIn(orderIds);

    List<CashSessionResponse> closings =
        (locationId != null ? cashSessionService.findAllByLocation(locationId) : cashSessionService.findAll()).stream()
            .filter(session -> session.status() == CashSessionStatus.CLOSED && session.closedAt() != null)
            .filter(session -> !session.closedAt().isBefore(from) && session.closedAt().isBefore(to))
            .sorted(Comparator.comparing(CashSessionResponse::closedAt))
            .toList();

    Set<String> userIds = new HashSet<>();
    orders.forEach(order -> userIds.add(order.getCreatedById()));
    discounts.forEach(discount -> userIds.add(discount.getCreatedById()));
    Map<String, String> userNames = new HashMap<>();
    if (!userIds.isEmpty()) {
      for (User user : userRepository.findAllByIdInScoped(userIds)) {
        userNames.put(user.getId(), (user.getFirstName() + " " + user.getLastName()).trim());
      }
    }
    Map<String, RestaurantTable> tables =
        tableRepository.findAllByOrderByNumberAsc().stream().collect(Collectors.toMap(RestaurantTable::getId, t -> t));

    Context ctx = new Context(zone, locations, tables, userNames);
    XlsxWriter book = new XlsxWriter();
    summarySheet(book, ctx, locationId, from, to, orders, items, payments);
    salesSheet(book, ctx, orders, items, payments);
    appsSheet(book, orders);
    productsSheet(book, items);
    closingsSheet(book, ctx, closings);
    discountsSheet(book, ctx, orders, items, discounts);

    String fileName =
        "reporte-" + from.atZone(zone).toLocalDate() + "-al-" + to.minusSeconds(1).atZone(zone).toLocalDate() + ".xlsx";
    return new ExcelReport(book.toBytes(), fileName);
  }

  private record Context(
      ZoneId zone, Map<String, Location> locations, Map<String, RestaurantTable> tables, Map<String, String> userNames) {

    LocalDateTime local(Instant instant) {
      return instant == null ? null : LocalDateTime.ofInstant(instant, zone);
    }

    String locationName(String id) {
      Location location = locations.get(id);
      return location != null ? location.getName() : "";
    }

    String tableName(String id) {
      if (id == null) return "Para llevar";
      RestaurantTable table = tables.get(id);
      if (table == null) return "Mesa";
      return table.getLabel() != null && !table.getLabel().isBlank() ? table.getLabel() : "Mesa " + table.getNumber();
    }

    String userName(String id) {
      return userNames.getOrDefault(id, "");
    }
  }

  // ---------------------------------------------------------------------
  // Hojas
  // ---------------------------------------------------------------------

  private void summarySheet(
      XlsxWriter book,
      Context ctx,
      String locationId,
      Instant from,
      Instant to,
      List<Order> orders,
      List<OrderItem> items,
      List<OrderPayment> payments) {
    BigDecimal sales = orders.stream().map(Order::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal discounted =
        orders.stream().map(Order::getDiscountTotal).reduce(BigDecimal.ZERO, BigDecimal::add)
            .add(items.stream().map(OrderItem::getDiscountAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    BigDecimal average =
        orders.isEmpty() ? BigDecimal.ZERO : sales.divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP);
    Map<PaymentMethod, BigDecimal> byMethod = new EnumMap<>(PaymentMethod.class);
    payments.forEach(payment -> byMethod.merge(payment.getPaymentMethod(), payment.getAmount(), BigDecimal::add));

    String businessName =
        businessRepository
            .findById(tenantContext.getBusinessId())
            .map(b -> b.getTradeName() != null && !b.getTradeName().isBlank() ? b.getTradeName() : b.getLegalName())
            .orElse("");

    XlsxWriter.Sheet sheet = book.sheet("Resumen").widths(32, 22);
    sheet.row(Cell.bold("Reporte de ventas"), Cell.text(businessName));
    sheet.row(Cell.text("Desde"), Cell.date(ctx.local(from).toLocalDate()));
    sheet.row(Cell.text("Hasta (inclusive)"), Cell.date(ctx.local(to.minusSeconds(1)).toLocalDate()));
    sheet.row(Cell.text("Local"), Cell.text(locationId != null ? ctx.locationName(locationId) : "Todos"));
    sheet.blank();
    sheet.row(Cell.bold("Ventas (pedidos entregados)"), Cell.boldMoney(sales));
    sheet.row(Cell.text("Cantidad de pedidos"), Cell.integer(orders.size()));
    sheet.row(Cell.text("Ticket promedio"), Cell.money(average));
    sheet.row(Cell.text("Descuentos otorgados"), Cell.money(discounted));
    sheet.blank();
    sheet.row(Cell.bold("Cobrado por método"));
    for (PaymentMethod method : METHODS) {
      if (method == PaymentMethod.APP && !byMethod.containsKey(PaymentMethod.APP)) continue; // café sin apps: igual que antes
      sheet.row(Cell.text(METHOD_LABELS.get(method)), Cell.money(byMethod.getOrDefault(method, BigDecimal.ZERO)));
    }
    // V19 — ventas por canal, solo si hubo pedidos de apps (un café sin apps no ve nada nuevo).
    if (orders.stream().anyMatch(Order::isFromChannel)) {
      sheet.blank();
      sheet.row(Cell.bold("Ventas por canal"));
      Map<String, BigDecimal> byChannel = new LinkedHashMap<>();
      byChannel.put("Salón y mostrador", BigDecimal.ZERO);
      for (Order order : orders) {
        byChannel.merge(order.isFromChannel() ? order.getChannelName() : "Salón y mostrador", order.getTotal(), BigDecimal::add);
      }
      byChannel.forEach((name, total) -> sheet.row(Cell.text(name), Cell.money(total)));
      sheet.row(Cell.text("Comisiones de apps (estimadas)"), Cell.money(orders.stream().map(ReportService::commission).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }
    sheet.blank();
    sheet.row(Cell.text("Solo cuenta pedidos entregados (cerrados y cobrados) en el período."));
    sheet.row(Cell.text("En \"Productos\", los descuentos sobre todo el pedido no se reparten entre los productos."));
    sheet.row(Cell.text("No es un comprobante fiscal."));
  }

  private void salesSheet(
      XlsxWriter book, Context ctx, List<Order> orders, List<OrderItem> items, List<OrderPayment> payments) {
    Map<String, Integer> unitsByOrder = new HashMap<>();
    Map<String, BigDecimal> itemDiscountByOrder = new HashMap<>();
    for (OrderItem item : items) {
      unitsByOrder.merge(item.getOrderId(), item.getQuantity(), Integer::sum);
      itemDiscountByOrder.merge(item.getOrderId(), item.getDiscountAmount(), BigDecimal::add);
    }
    Map<String, Map<PaymentMethod, BigDecimal>> paidByOrder = new HashMap<>();
    for (OrderPayment payment : payments) {
      paidByOrder
          .computeIfAbsent(payment.getOrderId(), k -> new EnumMap<>(PaymentMethod.class))
          .merge(payment.getPaymentMethod(), payment.getAmount(), BigDecimal::add);
    }

    // V19 — las columnas de apps solo aparecen si hubo pedidos de apps.
    boolean apps = orders.stream().anyMatch(Order::isFromChannel);
    List<PaymentMethod> methods = apps ? METHODS : METHODS.stream().filter(m -> m != PaymentMethod.APP).toList();
    XlsxWriter.Sheet sheet =
        apps
            ? book.sheet("Ventas").widths(17, 12, 18, 20, 20, 7, 13, 13, 13, 13, 13, 13, 14, 13, 13, 14, 14)
            : book.sheet("Ventas").widths(17, 12, 18, 13, 20, 7, 13, 13, 13, 13, 13, 13, 14, 13, 13);
    List<String> titles =
        new ArrayList<>(List.of("Fecha", "Pedido", "Local", apps ? "Dónde" : "Mesa", "Atendió", "Ítems", "Subtotal", "Descuentos", "Total"));
    methods.forEach(m -> titles.add(m == PaymentMethod.APP ? "App de delivery" : METHOD_LABELS.get(m)));
    if (apps) titles.add("Comisión app");
    sheet.header(titles.toArray(String[]::new));
    for (Order order : orders) {
      BigDecimal discount = order.getDiscountTotal().add(itemDiscountByOrder.getOrDefault(order.getId(), BigDecimal.ZERO));
      Map<PaymentMethod, BigDecimal> paid = paidByOrder.getOrDefault(order.getId(), Map.of());
      List<Cell> cells = new ArrayList<>();
      cells.add(Cell.dateTime(ctx.local(order.getClosedAt())));
      cells.add(Cell.text("#" + order.getId().substring(0, 8).toUpperCase()));
      cells.add(Cell.text(ctx.locationName(order.getLocationId())));
      cells.add(Cell.text(order.isFromChannel() ? order.getChannelName() + " #" + order.getChannelOrderRef() : ctx.tableName(order.getTableId())));
      cells.add(Cell.text(ctx.userName(order.getCreatedById())));
      cells.add(Cell.integer(unitsByOrder.getOrDefault(order.getId(), 0)));
      cells.add(Cell.money(order.getTotal().add(discount)));
      cells.add(Cell.money(discount));
      cells.add(Cell.money(order.getTotal()));
      for (PaymentMethod method : methods) {
        BigDecimal amount = paid.get(method);
        cells.add(amount != null ? Cell.money(amount) : Cell.empty());
      }
      if (apps) cells.add(order.isFromChannel() ? Cell.money(commission(order)) : Cell.empty());
      sheet.row(cells.toArray(Cell[]::new));
    }
    sheet.totals(
        "Total",
        apps ? new int[] {6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16} : new int[] {6, 7, 8, 9, 10, 11, 12, 13, 14},
        5);
  }

  /** V19 — una fila por app: pedidos, ventas, comisión y lo que le queda al café. Solo si hubo pedidos de apps. */
  private void appsSheet(XlsxWriter book, List<Order> orders) {
    List<Order> appOrders = orders.stream().filter(Order::isFromChannel).toList();
    if (appOrders.isEmpty()) return;
    Map<String, long[]> count = new LinkedHashMap<>();
    Map<String, BigDecimal> sales = new LinkedHashMap<>();
    Map<String, BigDecimal> fees = new LinkedHashMap<>();
    for (Order order : appOrders) {
      String name = order.getChannelName();
      count.computeIfAbsent(name, k -> new long[1])[0]++;
      sales.merge(name, order.getTotal(), BigDecimal::add);
      fees.merge(name, commission(order), BigDecimal::add);
    }
    XlsxWriter.Sheet sheet = book.sheet("Apps de delivery").widths(24, 11, 16, 16, 18);
    sheet.header("App", "Pedidos", "Ventas", "Comisión", "Le queda al café");
    sales.forEach(
        (name, total) ->
            sheet.row(
                Cell.text(name),
                Cell.integer(count.get(name)[0]),
                Cell.money(total),
                Cell.money(fees.get(name)),
                Cell.money(total.subtract(fees.get(name)))));
    sheet.totals("Total", new int[] {2, 3, 4}, 1);
    sheet.blank();
    sheet.row(Cell.text("La comisión es la que cargó el café en \"Apps de delivery\" al momento de cada pedido: es una estimación."));
    sheet.row(Cell.text("Lo que realmente liquida cada app está en su propio resumen."));
  }

  /** Comisión estimada de un pedido de app (total × % del canal al cargarlo). */
  private static BigDecimal commission(Order order) {
    if (!order.isFromChannel() || order.getChannelCommissionPct() == null) return BigDecimal.ZERO;
    return order.getTotal().multiply(order.getChannelCommissionPct()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
  }

  private void productsSheet(XlsxWriter book, List<OrderItem> items) {
    Map<String, long[]> units = new LinkedHashMap<>();
    Map<String, BigDecimal> revenue = new LinkedHashMap<>();
    for (OrderItem item : items) {
      units.computeIfAbsent(item.getProductName(), k -> new long[1])[0] += item.getQuantity();
      revenue.merge(item.getProductName(), item.getSubtotal(), BigDecimal::add);
    }
    XlsxWriter.Sheet sheet = book.sheet("Productos").widths(34, 11, 16);
    sheet.header("Producto", "Unidades", "Total vendido");
    revenue.entrySet().stream()
        .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
        .forEach(
            entry ->
                sheet.row(
                    Cell.text(entry.getKey()), Cell.integer(units.get(entry.getKey())[0]), Cell.money(entry.getValue())));
    sheet.totals("Total", new int[] {2}, 1);
  }

  private void closingsSheet(XlsxWriter book, Context ctx, List<CashSessionResponse> closings) {
    XlsxWriter.Sheet sheet =
        book.sheet("Cierres de caja").widths(18, 17, 18, 17, 18, 12, 13, 13, 13, 14, 13, 13, 12, 12, 14, 13, 12, 30);
    sheet.header(
        "Local", "Abrió el", "Abrió", "Cerró el", "Cerró", "Inicial", "Efectivo", "Débito", "Crédito",
        "Transferencia", "QR", "Otro", "Ingresos", "Egresos", "Esperado en caja", "Contado", "Diferencia", "Notas");
    for (CashSessionResponse session : closings) {
      List<Cell> cells = new ArrayList<>();
      cells.add(Cell.text(ctx.locationName(session.locationId())));
      cells.add(Cell.dateTime(ctx.local(session.openedAt())));
      cells.add(Cell.text(session.openedByName()));
      cells.add(Cell.dateTime(ctx.local(session.closedAt())));
      cells.add(Cell.text(session.closedByName()));
      cells.add(Cell.money(session.openingAmount()));
      Map<PaymentMethod, BigDecimal> sales = session.salesByMethod() != null ? session.salesByMethod() : Map.of();
      for (PaymentMethod method : METHODS) {
        cells.add(Cell.money(sales.getOrDefault(method, BigDecimal.ZERO)));
      }
      cells.add(Cell.money(session.cashIncomeMovementsTotal()));
      cells.add(Cell.money(session.cashExpenseMovementsTotal()));
      cells.add(Cell.money(session.expectedCashAmount()));
      cells.add(Cell.money(session.countedCashAmount()));
      cells.add(Cell.money(session.cashDifference()));
      cells.add(Cell.text(session.notes()));
      sheet.row(cells.toArray(Cell[]::new));
    }
    sheet.totals("Total", new int[] {6, 7, 8, 9, 10, 11, 12, 13, 16});
  }

  private void discountsSheet(
      XlsxWriter book, Context ctx, List<Order> orders, List<OrderItem> items, List<OrderDiscount> discounts) {
    Map<String, Order> ordersById = orders.stream().collect(Collectors.toMap(Order::getId, o -> o));
    Map<String, OrderItem> itemsById = items.stream().collect(Collectors.toMap(OrderItem::getId, i -> i));
    XlsxWriter.Sheet sheet = book.sheet("Descuentos").widths(17, 11, 18, 24, 8, 9, 28, 13, 20, 34);
    sheet.header("Fecha", "Pedido", "Local", "Descuento", "Tipo", "Valor", "Aplicado a", "Monto", "Aplicó", "Motivo");
    discounts.stream()
        .sorted(Comparator.comparing(OrderDiscount::getCreatedAt))
        .forEach(
            discount -> {
              Order order = ordersById.get(discount.getOrderId());
              OrderItem item = discount.getOrderItemId() != null ? itemsById.get(discount.getOrderItemId()) : null;
              String target =
                  discount.getOrderItemId() == null
                      ? "Todo el pedido"
                      : item != null ? item.getQuantity() + "× " + item.getProductName() : "Un producto";
              sheet.row(
                  Cell.dateTime(ctx.local(discount.getCreatedAt())),
                  Cell.text(order != null ? "#" + order.getId().substring(0, 8).toUpperCase() : ""),
                  Cell.text(order != null ? ctx.locationName(order.getLocationId()) : ""),
                  Cell.text(discount.getName()),
                  Cell.text(discount.getType() == DiscountType.PERCENTAGE ? "%" : "$"),
                  Cell.decimal(discount.getValue()),
                  Cell.text(target),
                  Cell.money(discount.getAmount()),
                  Cell.text(ctx.userName(discount.getCreatedById())),
                  Cell.text(discount.getReason()));
            });
    sheet.totals("Total", new int[] {7});
  }
}
