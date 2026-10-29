package com.cofeapp.backend.order;

import com.cofeapp.backend.cash.CashSessionRepository;
import com.cofeapp.backend.common.context.TenantContext;
import com.cofeapp.backend.common.exception.ConflictException;
import com.cofeapp.backend.common.exception.NotFoundException;
import com.cofeapp.backend.common.exception.BadRequestException;
import com.cofeapp.backend.common.exception.ForbiddenException;
import com.cofeapp.backend.discount.DiscountRepository;
import com.cofeapp.backend.domain.CashSession;
import com.cofeapp.backend.domain.Discount;
import com.cofeapp.backend.domain.OrderDiscount;
import com.cofeapp.backend.domain.enums.DiscountType;
import com.cofeapp.backend.domain.MpQrCharge;
import com.cofeapp.backend.domain.Order;
import com.cofeapp.backend.domain.OrderItem;
import com.cofeapp.backend.domain.OrderItemOption;
import com.cofeapp.backend.domain.OrderPayment;
import com.cofeapp.backend.domain.OrderPaymentItem;
import com.cofeapp.backend.domain.Product;
import com.cofeapp.backend.domain.RestaurantTable;
import com.cofeapp.backend.domain.User;
import com.cofeapp.backend.channel.SalesChannelRepository;
import com.cofeapp.backend.domain.SalesChannel;
import com.cofeapp.backend.domain.enums.CashSessionStatus;
import com.cofeapp.backend.domain.enums.SalesChannelMode;
import com.cofeapp.backend.domain.enums.OrderStatus;
import com.cofeapp.backend.domain.enums.PaymentMethod;
import com.cofeapp.backend.domain.enums.QrChargeStatus;
import com.cofeapp.backend.domain.enums.TableStatus;
import com.cofeapp.backend.domain.enums.UserRole;
import com.cofeapp.backend.location.LocationRepository;
import com.cofeapp.backend.mercadopago.MpQrChargeRepository;
import com.cofeapp.backend.option.OptionSelector;
import com.cofeapp.backend.option.OrderItemOptionRepository;
import com.cofeapp.backend.order.dto.AddOrderItemsRequest;
import com.cofeapp.backend.order.dto.ApplyDiscountRequest;
import com.cofeapp.backend.order.dto.ChannelOrderInput;
import com.cofeapp.backend.order.dto.OrderDiscountResponse;
import com.cofeapp.backend.order.dto.OrderPaymentItemResponse;
import com.cofeapp.backend.order.dto.PaymentItemInput;
import com.cofeapp.backend.order.dto.CreateOrderItemRequest;
import com.cofeapp.backend.order.dto.CreateOrderPaymentRequest;
import com.cofeapp.backend.order.dto.CreateOrderRequest;
import com.cofeapp.backend.order.dto.OrderItemOptionResponse;
import com.cofeapp.backend.order.dto.OrderItemResponse;
import com.cofeapp.backend.order.dto.OrderPaymentResponse;
import com.cofeapp.backend.order.dto.OrderResponse;
import com.cofeapp.backend.order.dto.UpdateOrderStatusRequest;
import com.cofeapp.backend.product.ProductRepository;
import com.cofeapp.backend.stock.StockService;
import com.cofeapp.backend.stock.StockService.IngredientUse;
import com.cofeapp.backend.stock.StockService.StockConsumptionLine;
import com.cofeapp.backend.table.TableRepository;
import com.cofeapp.backend.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El corazón de la Etapa 2 (más el cobro de pedidos del Bloque C/Etapa 3).
 * Cinco reglas de negocio que no son un simple CRUD, documentadas junto a
 * cada método:
 *
 * <ol>
 *   <li>Idempotencia de pedido ({@link #create}): reintentar el mismo
 *       {@code idempotencyKey} nunca duplica el pedido.
 *   <li>Una mesa, un pedido abierto a la vez ({@link #assertTableFree}):
 *       con lock pesimista sobre la mesa para que dos creaciones
 *       concurrentes no se cuelen ambas.
 *   <li>Máquina de estados con rol habilitado por transición ({@link
 *       #assertTransitionAllowed}): KITCHEN prepara, mesera/supervisor/
 *       admin/owner crean y entregan, todos menos KITCHEN pueden cancelar.
 *   <li>Cobro dividido entre métodos ({@link #addPayment}), atado a un
 *       turno de caja abierto, y exigido por completo antes de DELIVERED
 *       ({@link #assertFullyPaid}) — ver el Javadoc de {@code OrderStatus}.
 *   <li>Tiempo real ({@link OrderEventPublisher}): {@link #create},
 *       {@link #updateStatus} y {@link #addPayment} publican al tópico WebSocket del local
 *       del pedido recién después de que la transacción confirma.
 * </ol>
 *
 * <p>Bloque D (ítems 12/13): {@link #create} descuenta stock automático
 * (vía {@link StockService#consumeForOrder}) para cada ítem cuyo producto
 * tenga {@code stockMode} DIRECT o RECIPE — apenas se crea el pedido, no
 * al entregarlo (así lo pidió Fran: se toma de la vitrina/se prepara en el
 * momento). Si el pedido se cancela, {@link #updateStatus} repone ese
 * mismo stock (vía {@link StockService#restoreForOrder}).
 */
@Service
@RequiredArgsConstructor
public class OrderService {

  /** Todo lo que no sea DELIVERED/CANCELLED cuenta como "pedido abierto" sobre una mesa. */
  private static final Set<OrderStatus> OPEN_STATUSES =
      Set.of(OrderStatus.PENDING, OrderStatus.IN_PREPARATION, OrderStatus.READY);

  private static final Set<UserRole> KITCHEN_ROLES = Set.of(UserRole.KITCHEN);
  private static final Set<UserRole> FRONT_OF_HOUSE_ROLES =
      Set.of(UserRole.WAITER, UserRole.SUPERVISOR, UserRole.ADMIN, UserRole.OWNER);

  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final OrderPaymentRepository orderPaymentRepository;
  private final ProductRepository productRepository;
  private final LocationRepository locationRepository;
  private final TableRepository tableRepository;
  private final CashSessionRepository cashSessionRepository;
  private final UserRepository userRepository;
  private final StockService stockService;
  private final TenantContext tenantContext;
  private final OrderEventPublisher orderEventPublisher;
  private final MpQrChargeRepository qrChargeRepository;
  private final OptionSelector optionSelector;
  private final OrderItemOptionRepository orderItemOptionRepository;
  private final OrderDiscountRepository orderDiscountRepository;
  private final DiscountRepository discountRepository;
  private final OrderPaymentItemRepository orderPaymentItemRepository;
  private final SalesChannelRepository salesChannelRepository;

  public List<OrderResponse> findAll() {
    return toResponses(orderRepository.findAllByOrderByCreatedAtDesc());
  }

  public List<OrderResponse> findAllByLocation(String locationId) {
    assertLocationExists(locationId);
    return toResponses(orderRepository.findAllByLocationIdOrderByCreatedAtDesc(locationId));
  }

  /**
   * Pedidos activos ({@code GET /orders/active}) — respaldo por polling (15-20s) para el KDS, para cuando todavía no
   * llegó (o se perdió) el evento por WebSocket del Bloque de gateway.
   */
  public List<OrderResponse> findActive() {
    return toResponses(orderRepository.findAllByStatusInOrderByCreatedAtAsc(OPEN_STATUSES));
  }

  /**
   * Misma idea que {@link #findActive}, acotada a un local — es la que
   * usa de verdad el frontend de KDS (ver {@code OrderController}): el
   * WebSocket ya te suscribe a un único {@code locationRoom} (ver {@code
   * OrderTopics}), así que el polling de respaldo tiene que traer
   * exactamente ese mismo subconjunto, no los pedidos de todo el negocio
   * (relevante ya desde hoy si una cocina de un local ve por error pedidos
   * de otro, y listo para el multi-sucursal de la Etapa 5).
   */
  public List<OrderResponse> findActiveByLocation(String locationId) {
    assertLocationExists(locationId);
    return toResponses(orderRepository.findAllByLocationIdAndStatusInOrderByCreatedAtAsc(locationId, OPEN_STATUSES));
  }

  /**
   * Para {@code OrderController#create}: si dos envíos del mismo pedido
   * (doble clic, reintento de la cola sin internet) llegan exactamente a la
   * vez, el segundo choca con el índice único de la clave al guardar. En ese
   * caso se devuelve el pedido que sí se creó.
   */
  @Transactional(readOnly = true)
  public java.util.Optional<OrderResponse> findByIdempotencyKey(String idempotencyKey) {
    return orderRepository.findByIdempotencyKey(idempotencyKey).map(this::toResponse);
  }

  public OrderResponse findOne(String id) {
    return toResponse(findOneOrThrow(id));
  }

  @Transactional
  public OrderResponse create(CreateOrderRequest request) {
    // Idempotencia: si ya existe un pedido con esta key para el negocio,
    // se devuelve ESE (soporta reintentos de una cola offline sin
    // duplicar) en vez de crear uno nuevo.
    var existing = orderRepository.findByIdempotencyKey(request.getIdempotencyKey());
    if (existing.isPresent()) {
      return toResponse(existing.get());
    }

    assertLocationExists(request.getLocationId());

    // V19 — pedido de una app de delivery (carga manual).
    SalesChannel channel = request.getChannel() != null ? resolveChannel(request) : null;

    RestaurantTable table = null;
    if (request.getTableId() != null) {
      // Lock pesimista: serializa dos creaciones concurrentes sobre la
      // MISMA mesa, para que assertTableFree no tenga ventana de carrera
      // (dos requests que pasan el chequeo "está libre" antes de que
      // cualquiera de las dos haya insertado su pedido).
      table =
          tableRepository
              .findByIdScopedForUpdate(request.getTableId())
              .orElseThrow(() -> new NotFoundException("No se encontró la mesa " + request.getTableId() + "."));
      // Un reintento del MISMO pedido que esperó el bloqueo de la mesa: ya está creado, se devuelve ese.
      var sameRequest = orderRepository.findByIdempotencyKey(request.getIdempotencyKey());
      if (sameRequest.isPresent()) {
        return toResponse(sameRequest.get());
      }
      assertTableFree(table.getId());
    }

    Order order = new Order();
    order.setLocationId(request.getLocationId());
    order.setTableId(request.getTableId());
    order.setCreatedById(tenantContext.getUserId());
    order.setNotes(request.getNotes());
    order.setIdempotencyKey(request.getIdempotencyKey());
    order.setClientTimestamp(request.getClientTimestamp());
    if (channel != null) {
      ChannelOrderInput input = request.getChannel();
      order.setChannelId(channel.getId());
      order.setChannelKind(channel.getKind());
      order.setChannelName(channel.getName());
      order.setChannelOrderRef(channelRef(input.orderRef()));
      order.setChannelCustomer(blankToNull(input.customerName()));
      order.setChannelPickupAt(input.pickupAt());
      order.setChannelPaidByApp(!Boolean.FALSE.equals(input.paidByApp()));
      order.setChannelCommissionPct(channel.getCommissionPct());
    }

    List<ItemBuild> itemBuilds = buildItems(request.getItems());
    List<OrderItem> items = itemBuilds.stream().map(ItemBuild::item).toList();
    BigDecimal subtotal = items.stream().map(OrderItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    order.setSubtotal(subtotal);
    order.setDiscountTotal(BigDecimal.ZERO); // Discounts diferido a la Etapa 3.
    order.setTotal(subtotal);

    try {
      order = orderRepository.save(order);
    } catch (DataIntegrityViolationException e) {
      // Dos requests concurrentes con el mismo idempotencyKey: la que
      // perdió la carrera contra el índice único (business_id,
      // idempotency_key) de la migración cae acá — se devuelve el pedido
      // que sí se llegó a crear, en vez de propagar el error al cliente.
      return orderRepository
          .findByIdempotencyKey(request.getIdempotencyKey())
          .map(this::toResponse)
          .orElseThrow(() -> e);
    }

    int line = 1;
    for (OrderItem item : items) {
      item.setOrderId(order.getId());
      item.setLineNumber(line++);
    }
    orderItemRepository.saveAll(items);
    saveOptions(itemBuilds);

    // Bloque D — descuento automático de stock (ítems DIRECT/RECIPE, más los
    // insumos de las opciones elegidas), apenas se crea el pedido — ver el
    // Javadoc de esta clase.
    stockService.consumeForOrder(stockLines(itemBuilds), order.getLocationId());

    if (table != null) {
      table.setStatus(TableStatus.OCCUPIED);
      tableRepository.save(table);
    }

    // V19 — si lo cobró la app, queda cobrado ("a cobrar de la app"): no
    // pasa por la caja del mostrador.
    if (Boolean.TRUE.equals(order.getChannelPaidByApp()) && order.getTotal().signum() > 0) {
      OrderPayment appPayment = new OrderPayment();
      appPayment.setOrderId(order.getId());
      appPayment.setCashSessionId(openCashSessionId(order.getLocationId()));
      appPayment.setPaymentMethod(PaymentMethod.APP);
      appPayment.setAmount(order.getTotal());
      appPayment.setCreatedById(tenantContext.getUserId());
      orderPaymentRepository.save(appPayment);
      OrderResponse paid = toResponse(order);
      orderEventPublisher.orderCreated(paid);
      return paid;
    }

    OrderResponse response =
        OrderResponse.from(
            order,
            itemBuilds.stream()
                .map(build -> OrderItemResponse.from(build.item(), toOptionResponses(build.options())))
                .toList(),
            List.of(),
            List.of());
    // Publicado recién tras el commit de esta transacción — ver el Javadoc
    // de OrderEventPublisher sobre por qué (evita que el KDS reciba un
    // pedido que un rollback tardío termina descartando).
    orderEventPublisher.orderCreated(response);
    return response;
  }

  @Transactional
  public OrderResponse updateStatus(String id, UpdateOrderStatusRequest request) {
    Order order = lockOrThrow(id);
    OrderStatus from = order.getStatus();
    OrderStatus to = request.getStatus();

    assertTransitionAllowed(from, to);
    if (to == OrderStatus.DELIVERED) {
      assertFullyPaid(order);
    }
    if (to == OrderStatus.CANCELLED) {
      assertNoPendingQrCharge(order.getId(), "cancelar el pedido");
      // Prueba de carga (oct. 2026): se podía cancelar un pedido ya cobrado y la
      // plata quedaba en la caja de un pedido anulado. Lo cobrado por una app
      // (pedido de app que la app cancela) no pasa por la caja: eso sí se puede.
      boolean cafeCharged =
          orderPaymentRepository.findAllByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
              .anyMatch(p -> p.getPaymentMethod() != PaymentMethod.APP);
      if (cafeCharged) {
        throw new ConflictException(
            "Este pedido ya tiene cobros registrados y no se puede cancelar. Si hay que devolver la plata, registrala como egreso en Caja.");
      }
    }

    order.setStatus(to);
    if (to == OrderStatus.DELIVERED || to == OrderStatus.CANCELLED) {
      order.setClosedAt(Instant.now());
      freeTableIfAny(order);
    }
    if (to == OrderStatus.CANCELLED) {
      // Bloque D — repone lo que consumeForOrder había descontado al crear el pedido.
      List<OrderItem> items = orderItemRepository.findAllByOrderIdOrderByLineNumberAscCreatedAtAsc(order.getId());
      Map<String, List<IngredientUse>> optionIngredients = new HashMap<>();
      optionsByItem(items)
          .forEach(
              (itemId, options) ->
                  optionIngredients.put(
                      itemId,
                      options.stream()
                          .filter(o -> o.getIngredientId() != null && o.getIngredientQuantity() != null)
                          .map(o -> new IngredientUse(o.getIngredientId(), o.getIngredientQuantity()))
                          .toList()));
      stockService.restoreForOrder(items, optionIngredients, order.getLocationId());
    }

    OrderResponse response = toResponse(orderRepository.save(order));
    orderEventPublisher.orderStatusChanged(response);
    return response;
  }

  /**
   * Hoja de ruta, paso 1 — agregar productos a un pedido que ya está
   * abierto ("la mesa pide otro cortado"), sin tener que cobrar, cerrar y
   * abrir otro pedido.
   *
   * <ul>
   *   <li>Solo pedidos abiertos (Nuevo / En preparación / Listo).
   *   <li>Los ítems nuevos entran como una tanda nueva ({@code batch} =
   *       la más alta + 1), con el mismo snapshot de nombre y precio que
   *       {@link #create} — ver {@link #buildItem}.
   *   <li>Descuentan stock igual que al crear el pedido.
   *   <li>Subtotal y total se recalculan sumando TODOS los ítems del
   *       pedido (no sumando un delta), así nunca se desincronizan de las
   *       líneas reales. El saldo a cobrar sube solo, porque se calcula al
   *       leer (total − cobros).
   *   <li>Decisión de Fran: si el pedido ya estaba En preparación o Listo,
   *       vuelve a Nuevo, para que Cocina lo vea sí o sí. Lo que ya se
   *       había preparado se distingue por la tanda.
   * </ul>
   *
   * <p>Simplificación aceptada: sin lock pesimista sobre el pedido. Dos
   * agregados simultáneos sobre el MISMO pedido podrían recalcular el total
   * sin ver los ítems del otro; el próximo agregado o cobro lo vuelve a
   * leer bien, y el caso (dos personas sumando a la misma mesa en el mismo
   * segundo) es muy raro. Si llega a pasar en el uso real, se agrega el
   * lock igual que en {@code TableRepository#findByIdScopedForUpdate}.
   */
  @Transactional
  public OrderResponse addItems(String id, AddOrderItemsRequest request) {
    Order order = lockOrThrow(id);
    if (!OPEN_STATUSES.contains(order.getStatus())) {
      throw new ConflictException("Ese pedido ya está cerrado: cargá un pedido nuevo.");
    }

    List<OrderItem> existing = orderItemRepository.findAllByOrderIdOrderByLineNumberAscCreatedAtAsc(order.getId());
    int nextBatch = existing.stream().mapToInt(item -> item.getBatch() != null ? item.getBatch() : 1).max().orElse(0) + 1;

    List<ItemBuild> itemBuilds = buildItems(request.getItems());
    List<OrderItem> added = itemBuilds.stream().map(ItemBuild::item).toList();
    int nextLine = existing.stream().mapToInt(item -> item.getLineNumber() != null ? item.getLineNumber() : 0).max().orElse(0);
    for (OrderItem item : added) {
      item.setOrderId(order.getId());
      item.setBatch(nextBatch);
      item.setLineNumber(++nextLine);
    }
    orderItemRepository.saveAll(added);
    saveOptions(itemBuilds);

    stockService.consumeForOrder(stockLines(itemBuilds), order.getLocationId());

    // Recalcula sumando TODOS los ítems, y los descuentos (un 10% sobre
    // todo el pedido pasa a incluir lo agregado).
    List<OrderItem> allItems = Stream.concat(existing.stream(), added.stream()).toList();
    List<OrderDiscount> discounts = orderDiscountRepository.findAllByOrderIdOrderByCreatedAtAsc(order.getId());
    recalculateTotals(order, allItems, discounts);
    orderItemRepository.saveAll(allItems);
    orderDiscountRepository.saveAll(discounts);

    if (order.getStatus() == OrderStatus.IN_PREPARATION || order.getStatus() == OrderStatus.READY) {
      order.setStatus(OrderStatus.PENDING);
    }

    OrderResponse response = toResponse(orderRepository.save(order));
    orderEventPublisher.orderUpdated(response);
    return response;
  }

  /**
   * Registra una línea de cobro sobre un pedido — ítems 5/11 de las
   * mejoras pedidas (pago dividido entre métodos: para "mitad efectivo,
   * mitad tarjeta" se llama dos veces, una por método). Requiere un turno
   * de caja ABIERTO en el local del pedido (ver {@link CashSessionRepository})
   * — sin eso no hay dónde totalizar el cobro al cerrar la caja (ítem 14) —
   * y no deja pagar de más: la suma de lo ya cobrado más este monto no
   * puede superar el total del pedido.
   */
  @Transactional
  public OrderResponse addPayment(String id, CreateOrderPaymentRequest request) {
    Order order = lockOrThrow(id);
    if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
      throw new ConflictException("No se pueden registrar cobros en un pedido ya cerrado.");
    }
    assertNoPendingQrCharge(order.getId(), "registrar otro cobro");

    // V19 — el cobro "App" es solo para pedidos de apps, y no necesita caja
    // abierta (no entra en la caja del mostrador).
    boolean appPayment = request.getPaymentMethod() == PaymentMethod.APP;
    if (appPayment && !order.isFromChannel()) {
      throw new BadRequestException("El cobro \"App\" es solo para pedidos de apps de delivery.");
    }
    CashSession cashSession =
        cashSessionRepository.findByLocationIdAndStatus(order.getLocationId(), CashSessionStatus.OPEN).orElse(null);
    if (cashSession == null && !appPayment) {
      throw new ConflictException("Abrí la caja del local antes de registrar un cobro.");
    }

    BigDecimal alreadyPaid = sumPayments(order.getId());
    BigDecimal balanceDue = order.getTotal().subtract(alreadyPaid);

    // V14 — "por lo que consumió cada uno": el monto sale de los productos elegidos.
    boolean byItems = request.getItems() != null && !request.getItems().isEmpty();
    List<OrderPaymentItem> paidLines = byItems ? buildPaidLines(order, request.getItems(), balanceDue) : List.of();
    BigDecimal amount =
        byItems ? paidLines.stream().map(OrderPaymentItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add) : request.getAmount();
    if (amount == null) {
      throw new BadRequestException("Indicá cuánto se cobra, o qué productos paga.");
    }
    if (amount.signum() <= 0) {
      throw new ConflictException(
          byItems ? "No queda nada para cobrar de esos productos." : "El monto tiene que ser mayor a 0.");
    }
    if (amount.compareTo(balanceDue) > 0) {
      throw new ConflictException(
          "Ese monto supera el saldo pendiente del pedido ($" + balanceDue.toPlainString() + ").");
    }

    OrderPayment payment = new OrderPayment();
    payment.setOrderId(order.getId());
    payment.setCashSessionId(cashSession != null ? cashSession.getId() : null);
    payment.setPaymentMethod(request.getPaymentMethod());
    payment.setAmount(amount);
    payment.setCreatedById(tenantContext.getUserId());
    payment = orderPaymentRepository.save(payment);
    for (OrderPaymentItem line : paidLines) {
      line.setOrderPaymentId(payment.getId());
    }
    orderPaymentItemRepository.saveAll(paidLines);

    OrderResponse response = toResponse(order);
    orderEventPublisher.orderUpdated(response);
    return response;
  }

  // ---------------------------------------------------------------------
  // Descuentos (V12)
  // ---------------------------------------------------------------------

  private static final Set<UserRole> SUPERVISOR_ROLES = Set.of(UserRole.SUPERVISOR, UserRole.ADMIN, UserRole.OWNER);
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  /**
   * Aplica un descuento a un ítem o a todo el pedido (V12). Reglas:
   *
   * <ul>
   *   <li>Solo pedidos abiertos, y sin un cobro con QR esperando.
   *   <li>De la lista: cualquiera que cobra, salvo los que "piden
   *       autorización". A mano (con motivo obligatorio) o que pide
   *       autorización: solo supervisor, administrador o dueño — decisión
   *       de Fran.
   *   <li>Uno por ítem y uno para todo el pedido: si ya había, se reemplaza
   *       (y reemplazar uno de supervisor también pide supervisor).
   *   <li>Nunca deja el total por debajo de lo que ya se cobró.
   * </ul>
   */
  @Transactional
  public OrderResponse applyDiscount(String orderId, ApplyDiscountRequest request) {
    Order order = lockOrThrow(orderId);
    assertOpenForDiscounts(order, "aplicar un descuento");

    List<OrderItem> items = orderItemRepository.findAllByOrderIdOrderByLineNumberAscCreatedAtAsc(order.getId());
    String itemId = request.getOrderItemId() == null || request.getOrderItemId().isBlank() ? null : request.getOrderItemId();
    if (itemId != null && items.stream().noneMatch(item -> item.getId().equals(itemId))) {
      throw new NotFoundException("Ese producto no es de este pedido.");
    }

    OrderDiscount applied = new OrderDiscount();
    applied.setOrderId(order.getId());
    applied.setOrderItemId(itemId);
    applied.setCreatedById(tenantContext.getUserId());
    if (request.getDiscountId() != null && !request.getDiscountId().isBlank()) {
      Discount preset =
          discountRepository
              .findByIdScoped(request.getDiscountId())
              .orElseThrow(() -> new NotFoundException("No se encontró el descuento " + request.getDiscountId() + "."));
      if (!Boolean.TRUE.equals(preset.getIsActive())) {
        throw new ConflictException("El descuento \"" + preset.getName() + "\" está desactivado.");
      }
      applied.setDiscountId(preset.getId());
      applied.setName(preset.getName());
      applied.setType(preset.getType());
      applied.setValue(preset.getValue());
      applied.setSupervisorOnly(Boolean.TRUE.equals(preset.getRequiresAuthorization()));
      applied.setReason(blankToNull(request.getReason()));
    } else {
      if (request.getType() == null || request.getValue() == null) {
        throw new BadRequestException("Elegí un descuento de la lista, o poné el porcentaje o el monto.");
      }
      String reason = blankToNull(request.getReason());
      if (reason == null) {
        throw new BadRequestException("Para un descuento a mano, escribí el motivo (queda registrado).");
      }
      if (request.getType() == DiscountType.PERCENTAGE && request.getValue().compareTo(HUNDRED) > 0) {
        throw new BadRequestException("Un descuento en porcentaje no puede pasar del 100%.");
      }
      applied.setName(
          request.getType() == DiscountType.PERCENTAGE
              ? "Descuento " + request.getValue().stripTrailingZeros().toPlainString() + "%"
              : "Descuento $" + request.getValue().stripTrailingZeros().toPlainString());
      applied.setType(request.getType());
      applied.setValue(request.getValue());
      applied.setSupervisorOnly(true);
      applied.setReason(reason);
    }
    if (Boolean.TRUE.equals(applied.getSupervisorOnly())) {
      assertSupervisor(
          request.getDiscountId() == null
              ? "Los descuentos a mano los aplica un supervisor, administrador o dueño."
              : "\"" + applied.getName() + "\" lo tiene que aplicar un supervisor, administrador o dueño.");
    }

    List<OrderDiscount> discounts = new ArrayList<>(orderDiscountRepository.findAllByOrderIdOrderByCreatedAtAsc(order.getId()));
    List<OrderDiscount> replaced =
        discounts.stream().filter(d -> java.util.Objects.equals(d.getOrderItemId(), itemId)).toList();
    for (OrderDiscount previous : replaced) {
      if (Boolean.TRUE.equals(previous.getSupervisorOnly())) {
        assertSupervisor("Ya tiene un descuento puesto por un supervisor: para cambiarlo hace falta un supervisor.");
      }
    }
    discounts.removeAll(replaced);
    discounts.add(applied);

    recalculateTotals(order, items, discounts);
    assertNotBelowPaid(order);

    orderDiscountRepository.deleteAll(replaced);
    orderDiscountRepository.flush();
    orderItemRepository.saveAll(items);
    orderDiscountRepository.saveAll(discounts);
    OrderResponse response = toResponse(orderRepository.save(order));
    orderEventPublisher.orderUpdated(response);
    return response;
  }

  /** Quita un descuento aplicado. Si lo había puesto un supervisor (o era a mano), solo un supervisor lo quita. */
  @Transactional
  public OrderResponse removeDiscount(String orderId, String orderDiscountId) {
    Order order = lockOrThrow(orderId);
    assertOpenForDiscounts(order, "quitar un descuento");
    List<OrderDiscount> discounts = new ArrayList<>(orderDiscountRepository.findAllByOrderIdOrderByCreatedAtAsc(order.getId()));
    OrderDiscount target =
        discounts.stream()
            .filter(d -> d.getId().equals(orderDiscountId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Ese descuento no está aplicado a este pedido."));
    if (Boolean.TRUE.equals(target.getSupervisorOnly())) {
      assertSupervisor("Ese descuento lo puso un supervisor: para quitarlo hace falta un supervisor.");
    }
    discounts.remove(target);

    List<OrderItem> items = orderItemRepository.findAllByOrderIdOrderByLineNumberAscCreatedAtAsc(order.getId());
    recalculateTotals(order, items, discounts);
    orderDiscountRepository.delete(target);
    orderItemRepository.saveAll(items);
    orderDiscountRepository.saveAll(discounts);
    OrderResponse response = toResponse(orderRepository.save(order));
    orderEventPublisher.orderUpdated(response);
    return response;
  }

  /**
   * Vuelve a calcular ítems y pedido a partir de los descuentos:
   * cada ítem = precio × cantidad − su descuento; subtotal = suma de los
   * ítems; total = subtotal − el descuento sobre todo el pedido. Un
   * porcentaje se redondea al centavo; un monto fijo nunca descuenta más
   * de lo que vale lo que descuenta.
   */
  private void recalculateTotals(Order order, List<OrderItem> items, List<OrderDiscount> discounts) {
    Map<String, OrderDiscount> byItem = new HashMap<>();
    OrderDiscount orderLevel = null;
    for (OrderDiscount discount : discounts) {
      if (discount.getOrderItemId() == null) orderLevel = discount;
      else byItem.put(discount.getOrderItemId(), discount);
    }

    BigDecimal subtotal = BigDecimal.ZERO;
    for (OrderItem item : items) {
      BigDecimal gross = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
      OrderDiscount discount = byItem.get(item.getId());
      BigDecimal amount = discount == null ? BigDecimal.ZERO : discountAmount(discount, gross);
      if (discount != null) discount.setAmount(amount);
      item.setDiscountAmount(amount);
      item.setDiscountId(discount != null ? discount.getDiscountId() : null);
      item.setSubtotal(gross.subtract(amount));
      subtotal = subtotal.add(item.getSubtotal());
    }

    BigDecimal orderAmount = orderLevel == null ? BigDecimal.ZERO : discountAmount(orderLevel, subtotal);
    if (orderLevel != null) orderLevel.setAmount(orderAmount);
    order.setSubtotal(subtotal);
    order.setDiscountTotal(orderAmount);
    order.setTotal(subtotal.subtract(orderAmount));
  }

  private static BigDecimal discountAmount(OrderDiscount discount, BigDecimal base) {
    BigDecimal amount =
        discount.getType() == DiscountType.PERCENTAGE
            ? base.multiply(discount.getValue()).divide(HUNDRED, 2, RoundingMode.HALF_UP)
            : discount.getValue();
    return amount.min(base).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
  }

  private void assertOpenForDiscounts(Order order, String action) {
    if (!OPEN_STATUSES.contains(order.getStatus())) {
      throw new ConflictException("Ese pedido ya está cerrado: no se puede " + action + ".");
    }
    assertNoPendingQrCharge(order.getId(), action);
  }

  private void assertNotBelowPaid(Order order) {
    BigDecimal paid = sumPayments(order.getId());
    if (order.getTotal().compareTo(paid) < 0) {
      throw new ConflictException(
          "Ya se cobraron $" + paid.toPlainString() + " de este pedido: con ese descuento el total quedaría en $"
              + order.getTotal().toPlainString() + ". Aplicá un descuento menor.");
    }
  }

  private void assertSupervisor(String message) {
    UserRole role = UserRole.valueOf(tenantContext.getRole());
    if (!SUPERVISOR_ROLES.contains(role)) {
      throw new ForbiddenException(message);
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  /**
   * V14 — arma qué paga un cobro "por productos" y cuánto.
   *
   * <p>Cada unidad vale lo que vale en el pedido: el neto del renglón
   * (con su descuento) dividido por la cantidad, y si hay un descuento
   * sobre todo el pedido, se reparte en proporción. Se redondea al
   * centavo; si con este cobro quedan pagadas TODAS las unidades del
   * pedido, se cobra exactamente el saldo (así los redondeos nunca dejan
   * centavos colgados). Nunca más que el saldo.
   */
  private List<OrderPaymentItem> buildPaidLines(Order order, List<PaymentItemInput> inputs, BigDecimal balanceDue) {
    List<OrderItem> items = orderItemRepository.findAllByOrderIdOrderByLineNumberAscCreatedAtAsc(order.getId());
    Map<String, OrderItem> byId = new HashMap<>();
    items.forEach(item -> byId.put(item.getId(), item));
    Map<String, Integer> alreadyPaid = paidQuantities(order.getId());

    Map<String, Integer> requested = new java.util.LinkedHashMap<>();
    for (PaymentItemInput input : inputs) {
      OrderItem item = byId.get(input.getOrderItemId());
      if (item == null) {
        throw new NotFoundException("Uno de los productos elegidos no es de este pedido.");
      }
      requested.merge(item.getId(), input.getQuantity(), Integer::sum);
    }

    BigDecimal factor =
        order.getSubtotal().signum() > 0
            ? order.getTotal().divide(order.getSubtotal(), 10, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
    List<OrderPaymentItem> lines = new ArrayList<>();
    for (Map.Entry<String, Integer> entry : requested.entrySet()) {
      OrderItem item = byId.get(entry.getKey());
      int remaining = item.getQuantity() - alreadyPaid.getOrDefault(item.getId(), 0);
      if (entry.getValue() > remaining) {
        throw new ConflictException(
            remaining == 0
                ? item.getProductName() + " ya está pagado."
                : "De " + item.getProductName() + " quedan " + remaining + " sin pagar.");
      }
      BigDecimal amount =
          item.getSubtotal()
              .multiply(BigDecimal.valueOf(entry.getValue()))
              .multiply(factor)
              .divide(BigDecimal.valueOf(item.getQuantity()), 2, RoundingMode.HALF_UP);
      OrderPaymentItem line = new OrderPaymentItem();
      line.setOrderItemId(item.getId());
      line.setQuantity(entry.getValue());
      line.setAmount(amount);
      lines.add(line);
    }

    // ¿Con este cobro queda todo pagado? Entonces se cobra justo el saldo.
    boolean coversEverything =
        items.stream()
            .allMatch(
                item ->
                    alreadyPaid.getOrDefault(item.getId(), 0) + requested.getOrDefault(item.getId(), 0)
                        >= item.getQuantity());
    BigDecimal sum = lines.stream().map(OrderPaymentItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal target = coversEverything ? balanceDue : sum.min(balanceDue);
    BigDecimal diff = target.subtract(sum);
    if (diff.signum() != 0 && !lines.isEmpty()) {
      // El ajuste (centavos de redondeo, o el tope por lo ya cobrado) va al último renglón.
      OrderPaymentItem last = lines.get(lines.size() - 1);
      last.setAmount(last.getAmount().add(diff).max(BigDecimal.ZERO));
    }
    return lines;
  }

  /**
   * V14 — cuánto se cobraría "por productos" (para el QR de Mercado Pago,
   * que necesita el monto antes de generar el cobro). Mismas validaciones
   * que {@link #addPayment}; no guarda nada.
   */
  public BigDecimal quoteItemsPayment(String orderId, List<PaymentItemInput> items) {
    Order order = findOneOrThrow(orderId);
    BigDecimal balanceDue = order.getTotal().subtract(sumPayments(order.getId()));
    BigDecimal amount =
        buildPaidLines(order, items, balanceDue).stream().map(OrderPaymentItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (amount.signum() <= 0) {
      throw new ConflictException("No queda nada para cobrar de esos productos.");
    }
    return amount;
  }

  /** "idDelRenglón:cantidad,…" — cómo el cobro con QR guarda qué productos paga. */
  public static String encodePaidItems(List<PaymentItemInput> items) {
    return items.stream().map(i -> i.getOrderItemId() + ":" + i.getQuantity()).collect(Collectors.joining(","));
  }

  static List<PaymentItemInput> decodePaidItems(String encoded) {
    if (encoded == null || encoded.isBlank()) return List.of();
    List<PaymentItemInput> items = new ArrayList<>();
    for (String part : encoded.split(",")) {
      String[] pieces = part.split(":");
      if (pieces.length != 2) continue;
      PaymentItemInput input = new PaymentItemInput();
      input.setOrderItemId(pieces[0]);
      input.setQuantity(Integer.parseInt(pieces[1]));
      items.add(input);
    }
    return items;
  }

  /** Unidades de cada renglón ya cobradas "por productos". */
  private Map<String, Integer> paidQuantities(String orderId) {
    List<String> paymentIds =
        orderPaymentRepository.findAllByOrderIdOrderByCreatedAtAsc(orderId).stream().map(OrderPayment::getId).toList();
    Map<String, Integer> paid = new HashMap<>();
    if (paymentIds.isEmpty()) return paid;
    for (OrderPaymentItem line : orderPaymentItemRepository.findAllByOrderPaymentIdIn(paymentIds)) {
      paid.merge(line.getOrderItemId(), line.getQuantity(), Integer::sum);
    }
    return paid;
  }

  /** Resultado de {@link #registerConfirmedQrPayment}: el id del cobro registrado, o por qué no se pudo. */
  public record QrPaymentResult(String paymentId, String problem) {
    public boolean registered() {
      return paymentId != null;
    }
  }

  /**
   * Registra como cobro del pedido (método QR) un pago que Mercado Pago ya
   * confirmó — lo llama {@code QrChargeService} dentro de su transacción,
   * con el cobro con QR bloqueado.
   *
   * <p>Diferencias con {@link #addPayment}: no lo dispara una persona en ese
   * momento (puede llegar desde el proceso de fondo o el webhook, sin
   * sesión), así que no usa {@link TenantContext}: el negocio y quién cobró
   * salen del cobro con QR. Y la plata ya entró, así que en vez de rechazar
   * devuelve el motivo si algo no cierra (pedido cerrado, sin caja abierta,
   * monto mayor al saldo), para que quede a la vista y se registre a mano.
   */
  public QrPaymentResult registerConfirmedQrPayment(MpQrCharge charge) {
    Order order = orderRepository.findByIdScopedForUpdate(charge.getOrderId()).orElse(null);
    if (order == null) {
      return new QrPaymentResult(null, "No se encontró el pedido del cobro.");
    }
    if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
      return new QrPaymentResult(null, "El pedido ya estaba cerrado cuando Mercado Pago confirmó el pago.");
    }
    CashSession cashSession =
        cashSessionRepository.findByLocationIdAndStatus(order.getLocationId(), CashSessionStatus.OPEN).orElse(null);
    if (cashSession == null) {
      return new QrPaymentResult(null, "No había una caja abierta cuando Mercado Pago confirmó el pago.");
    }
    BigDecimal balanceDue = order.getTotal().subtract(sumPayments(order.getId()));
    if (charge.getAmount().compareTo(balanceDue) > 0) {
      return new QrPaymentResult(
          null, "El pago supera lo que faltaba cobrar del pedido ($" + balanceDue.toPlainString() + ").");
    }

    // V14 — si el QR era "por productos", se marcan esos productos. Si ya
    // no se puede (caso raro: alguien los cobró por otro lado), el pago se
    // registra igual, como un cobro común: la plata ya entró.
    List<OrderPaymentItem> paidLines = List.of();
    List<PaymentItemInput> paidItems = decodePaidItems(charge.getPaidItems());
    if (!paidItems.isEmpty()) {
      try {
        paidLines = buildPaidLines(order, paidItems, balanceDue);
        BigDecimal sum = paidLines.stream().map(OrderPaymentItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        OrderPaymentItem last = paidLines.get(paidLines.size() - 1);
        last.setAmount(last.getAmount().add(charge.getAmount().subtract(sum)).max(BigDecimal.ZERO));
      } catch (RuntimeException e) {
        paidLines = List.of();
      }
    }

    OrderPayment payment = new OrderPayment();
    payment.setBusinessId(charge.getBusinessId());
    payment.setOrderId(order.getId());
    payment.setCashSessionId(cashSession.getId());
    payment.setPaymentMethod(PaymentMethod.QR);
    payment.setAmount(charge.getAmount());
    payment.setCreatedById(charge.getCreatedById());
    payment = orderPaymentRepository.save(payment);
    for (OrderPaymentItem line : paidLines) {
      // Puede llegar desde el proceso de fondo, sin sesión: el negocio sale del cobro.
      line.setBusinessId(charge.getBusinessId());
      line.setOrderPaymentId(payment.getId());
    }
    orderPaymentItemRepository.saveAll(paidLines);

    orderEventPublisher.orderUpdated(toResponse(order));
    return new QrPaymentResult(payment.getId(), null);
  }

  /**
   * Mientras un cobro con QR espera el pago, no se registran otros cobros
   * ni se cancela el pedido: si el cliente paga el QR después, se cobraría
   * dos veces (o se cobraría un pedido cancelado).
   */
  private void assertNoPendingQrCharge(String orderId, String action) {
    if (qrChargeRepository.existsByOrderIdAndStatus(orderId, QrChargeStatus.PENDING)) {
      throw new ConflictException(
          "Hay un cobro con QR de Mercado Pago esperando el pago de este pedido. Esperá a que se confirme o"
              + " cancelalo antes de " + action + ".");
    }
  }

  /** Ver el Javadoc de {@code OrderStatus} sobre por qué DELIVERED exige el pedido cobrado por completo. */
  private void assertFullyPaid(Order order) {
    BigDecimal paidTotal = sumPayments(order.getId());
    if (paidTotal.compareTo(order.getTotal()) < 0) {
      BigDecimal missing = order.getTotal().subtract(paidTotal);
      throw new ConflictException(
          "El pedido todavía no está cobrado por completo. Falta $" + missing.toPlainString() + ".");
    }
  }

  private BigDecimal sumPayments(String orderId) {
    return orderPaymentRepository.findAllByOrderIdOrderByCreatedAtAsc(orderId).stream()
        .map(OrderPayment::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /**
   * El {@link Product} y las opciones elegidas viajan junto al {@link
   * OrderItem} ya construido — {@link #create} los reusa para guardar las
   * opciones y descontar stock, sin volver a pedirlos (ver {@link
   * StockService#consumeForOrder}).
   */
  private record ItemBuild(OrderItem item, Product product, List<OrderItemOption> options) {}

  /** Guarda las opciones elegidas de cada ítem (los ítems ya tienen id). */
  private void saveOptions(List<ItemBuild> builds) {
    List<OrderItemOption> all = new ArrayList<>();
    for (ItemBuild build : builds) {
      int position = 0;
      for (OrderItemOption option : build.options()) {
        option.setOrderItemId(build.item().getId());
        option.setPosition(position++);
        all.add(option);
      }
    }
    if (!all.isEmpty()) orderItemOptionRepository.saveAll(all);
  }

  private static List<StockConsumptionLine> stockLines(List<ItemBuild> builds) {
    return builds.stream()
        .map(
            build ->
                new StockConsumptionLine(
                    build.product(),
                    build.item().getQuantity(),
                    build.options().stream()
                        .filter(o -> o.getIngredientId() != null && o.getIngredientQuantity() != null)
                        .map(o -> new IngredientUse(o.getIngredientId(), o.getIngredientQuantity()))
                        .toList()))
        .toList();
  }

  private Map<String, List<OrderItemOption>> optionsByItem(List<OrderItem> items) {
    if (items.isEmpty()) return Map.of();
    return orderItemOptionRepository.findAllByOrderItemIdInOrderByPositionAsc(items.stream().map(OrderItem::getId).toList()).stream()
        .collect(Collectors.groupingBy(OrderItemOption::getOrderItemId));
  }

  private static List<OrderItemOptionResponse> toOptionResponses(List<OrderItemOption> options) {
    return options.stream().map(OrderItemOptionResponse::from).toList();
  }

  private List<ItemBuild> buildItems(List<CreateOrderItemRequest> requests) {
    return requests.stream().map(this::buildItem).toList();
  }

  private ItemBuild buildItem(CreateOrderItemRequest request) {
    Product product =
        productRepository
            .findByIdScoped(request.getProductId())
            .orElseThrow(() -> new NotFoundException("No se encontró el producto " + request.getProductId() + "."));
    if (!Boolean.TRUE.equals(product.getIsActive())) {
      throw new ConflictException("El producto " + product.getName() + " está dado de baja.");
    }

    // V11 — valida las opciones elegidas y arma su copia (ver OptionSelector).
    List<OrderItemOption> options = optionSelector.resolve(product, request.getOptionIds());
    BigDecimal unitPrice =
        options.stream().map(OrderItemOption::getPriceDelta).reduce(product.getPrice(), BigDecimal::add);

    OrderItem item = new OrderItem();
    item.setProductId(product.getId());
    // Snapshot: nombre y precio se copian ACÁ, al momento de la venta —
    // nunca se vuelven a leer de Product después de esto (ver el Javadoc
    // de OrderItem). El precio unitario ya incluye los extras de las
    // opciones elegidas.
    item.setProductName(product.getName());
    item.setUnitPrice(unitPrice);
    item.setQuantity(request.getQuantity());
    item.setDiscountAmount(BigDecimal.ZERO); // Discounts diferido a la Etapa 3.
    item.setSubtotal(unitPrice.multiply(BigDecimal.valueOf(request.getQuantity())));
    item.setNotes(request.getNotes());
    return new ItemBuild(item, product, options);
  }

  /** Regla "una mesa, un pedido abierto a la vez". */
  private void assertTableFree(String tableId) {
    if (orderRepository.existsByTableIdAndStatusIn(tableId, OPEN_STATUSES)) {
      throw new ConflictException("Esa mesa ya tiene un pedido abierto.");
    }
  }

  /** Al cerrar (DELIVERED/CANCELLED) un pedido con mesa asociada, la mesa vuelve a quedar libre. */
  private void freeTableIfAny(Order order) {
    if (order.getTableId() == null) return;
    tableRepository
        .findByIdScoped(order.getTableId())
        .ifPresent(
            table -> {
              table.setStatus(TableStatus.FREE);
              tableRepository.save(table);
            });
  }

  /**
   * Valida que la transición {@code from -> to} sea legal y que el rol del
   * usuario autenticado (vía {@link TenantContext}) esté habilitado para
   * ejecutarla — ver {@link #allowedRolesFor} para la tabla completa.
   */
  private void assertTransitionAllowed(OrderStatus from, OrderStatus to) {
    Set<UserRole> allowedRoles = allowedRolesFor(from, to);
    if (allowedRoles.isEmpty()) {
      throw new ConflictException("No se puede pasar un pedido de " + from + " a " + to + ".");
    }

    UserRole actingRole = UserRole.valueOf(tenantContext.getRole());
    if (!allowedRoles.contains(actingRole)) {
      throw new AccessDeniedException(
          "Tu rol (" + actingRole + ") no puede mover un pedido de " + from + " a " + to + ".");
    }
  }

  /**
   * Máquina de estados PENDING→IN_PREPARATION→READY→DELIVERED, con
   * CANCELLED alcanzable desde cualquier estado no terminal. Rol habilitado
   * por transición: KITCHEN mueve PENDING→IN_PREPARATION→READY; mesera/
   * supervisor/admin/owner crean el pedido (ver {@link #create}, protegido
   * en el controller) y lo pasan a DELIVERED; cualquiera de esos roles
   * (nunca KITCHEN) puede cancelar mientras no esté DELIVERED. Devuelve un
   * set vacío para cualquier transición no contemplada acá (incluida
   * cualquiera desde DELIVERED/CANCELLED, que son terminales).
   */
  private Set<UserRole> allowedRolesFor(OrderStatus from, OrderStatus to) {
    return switch (from) {
      case PENDING -> switch (to) {
        case IN_PREPARATION -> KITCHEN_ROLES;
        case CANCELLED -> FRONT_OF_HOUSE_ROLES;
        default -> Set.of();
      };
      case IN_PREPARATION -> switch (to) {
        case READY -> KITCHEN_ROLES;
        case CANCELLED -> FRONT_OF_HOUSE_ROLES;
        default -> Set.of();
      };
      case READY -> switch (to) {
        case DELIVERED, CANCELLED -> FRONT_OF_HOUSE_ROLES;
        default -> Set.of();
      };
      default -> Set.of(); // DELIVERED/CANCELLED son terminales.
    };
  }

  private void assertLocationExists(String locationId) {
    locationRepository
        .findByIdScoped(locationId)
        .orElseThrow(() -> new NotFoundException("No se encontró el local " + locationId + "."));
  }

  private Order findOneOrThrow(String id) {
    return orderRepository
        .findByIdScoped(id)
        .orElseThrow(() -> new NotFoundException("No se encontró el pedido " + id + "."));
  }

  /** Para todo lo que CAMBIA un pedido: lo bloquea hasta terminar (ver {@code OrderRepository#findByIdScopedForUpdate}). */
  private Order lockOrThrow(String id) {
    return orderRepository
        .findByIdScopedForUpdate(id)
        .orElseThrow(() -> new NotFoundException("No se encontró el pedido " + id + "."));
  }

  // ---------------------------------------------------------------------
  // V19 — pedidos de apps de delivery
  // ---------------------------------------------------------------------

  /**
   * Valida un pedido de app: la app tiene que ser de este local y estar
   * prendida, el pedido no va a una mesa y su número no puede estar ya
   * cargado (así no se duplica si dos personas lo cargan).
   */
  private SalesChannel resolveChannel(CreateOrderRequest request) {
    if (request.getTableId() != null) {
      throw new BadRequestException("Un pedido de app no va a una mesa.");
    }
    ChannelOrderInput input = request.getChannel();
    SalesChannel channel =
        salesChannelRepository
            .findByIdScoped(input.channelId())
            .orElseThrow(() -> new NotFoundException("No se encontró esa app."));
    if (!channel.getLocationId().equals(request.getLocationId())) {
      throw new BadRequestException("Esa app es de otro local.");
    }
    if (channel.getMode() == SalesChannelMode.OFF) {
      throw new ConflictException(channel.getName() + " está apagada en este local. Prendela en \"Apps de delivery\".");
    }
    String ref = channelRef(input.orderRef());
    if (ref.isEmpty()) {
      throw new BadRequestException("Poné el número del pedido en la app.");
    }
    if (orderRepository.existsByChannelIdAndChannelOrderRefIgnoreCase(channel.getId(), ref)) {
      throw new ConflictException("El pedido #" + ref + " de " + channel.getName() + " ya está cargado.");
    }
    return channel;
  }

  /** "#1234 " → "1234": el número tal como lo muestra la app, sin el numeral. */
  private static String channelRef(String raw) {
    String ref = raw == null ? "" : raw.trim();
    while (ref.startsWith("#")) ref = ref.substring(1).trim();
    return ref;
  }

  private String openCashSessionId(String locationId) {
    return cashSessionRepository
        .findByLocationIdAndStatus(locationId, CashSessionStatus.OPEN)
        .map(CashSession::getId)
        .orElse(null);
  }

  private OrderResponse toResponse(Order order) {
    return toResponses(List.of(order)).get(0);
  }

  /**
   * Arma la respuesta de varios pedidos juntos, con una consulta por tipo
   * de dato (ítems, opciones, cobros, descuentos, nombres) para TODOS los
   * pedidos, en vez de varias consultas por pedido. "Pedidos abiertos" se
   * consulta cada 20 s desde cada mozo y desde la cocina: con 30 pedidos
   * abiertos pasó de ~180 consultas a la base a 6 (prueba de carga, oct.
   * 2026).
   */
  private List<OrderResponse> toResponses(List<Order> orders) {
    if (orders.isEmpty()) return List.of();
    List<String> orderIds = orders.stream().map(Order::getId).toList();

    Map<String, List<OrderItem>> itemsByOrder = new HashMap<>();
    orderItemRepository.findAllByOrderIdIn(orderIds).stream()
        .sorted(
            java.util.Comparator.comparing((OrderItem i) -> i.getLineNumber() == null ? Integer.MAX_VALUE : i.getLineNumber())
                .thenComparing(OrderItem::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
        .forEach(i -> itemsByOrder.computeIfAbsent(i.getOrderId(), k -> new ArrayList<>()).add(i));
    List<OrderItem> allItems = itemsByOrder.values().stream().flatMap(List::stream).toList();
    Map<String, List<OrderItemOption>> options = optionsByItem(allItems);

    Map<String, List<OrderPayment>> paymentsByOrder = new HashMap<>();
    List<OrderPayment> allPayments =
        orderPaymentRepository.findAllByOrderIdIn(orderIds).stream()
            .sorted(java.util.Comparator.comparing(OrderPayment::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
            .toList();
    allPayments.forEach(p -> paymentsByOrder.computeIfAbsent(p.getOrderId(), k -> new ArrayList<>()).add(p));
    List<OrderPaymentItem> paidLines =
        allPayments.isEmpty()
            ? List.of()
            : orderPaymentItemRepository.findAllByOrderPaymentIdIn(allPayments.stream().map(OrderPayment::getId).toList());
    Map<String, Integer> paidByItem = new HashMap<>();
    Map<String, List<OrderPaymentItemResponse>> linesByPayment = new HashMap<>();
    for (OrderPaymentItem line : paidLines) {
      paidByItem.merge(line.getOrderItemId(), line.getQuantity(), Integer::sum);
      linesByPayment.computeIfAbsent(line.getOrderPaymentId(), k -> new ArrayList<>()).add(OrderPaymentItemResponse.from(line));
    }

    Map<String, List<OrderDiscount>> discountsByOrder = new HashMap<>();
    orderDiscountRepository.findAllByOrderIdIn(orderIds).stream()
        .sorted(java.util.Comparator.comparing(OrderDiscount::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
        .forEach(d -> discountsByOrder.computeIfAbsent(d.getOrderId(), k -> new ArrayList<>()).add(d));

    Set<String> userIds = new java.util.HashSet<>();
    allPayments.forEach(p -> userIds.add(p.getCreatedById()));
    discountsByOrder.values().forEach(list -> list.forEach(d -> userIds.add(d.getCreatedById())));
    Map<String, String> names = resolveUserNames(userIds);

    List<OrderResponse> responses = new ArrayList<>(orders.size());
    for (Order order : orders) {
      List<OrderItemResponse> items =
          itemsByOrder.getOrDefault(order.getId(), List.of()).stream()
              .map(
                  item ->
                      OrderItemResponse.from(
                          item,
                          toOptionResponses(options.getOrDefault(item.getId(), List.of())),
                          paidByItem.getOrDefault(item.getId(), 0)))
              .toList();
      List<OrderPaymentResponse> payments =
          paymentsByOrder.getOrDefault(order.getId(), List.of()).stream()
              .map(p -> OrderPaymentResponse.from(p, names.get(p.getCreatedById()), linesByPayment.getOrDefault(p.getId(), List.of())))
              .toList();
      List<OrderDiscountResponse> discounts =
          discountsByOrder.getOrDefault(order.getId(), List.of()).stream()
              .map(d -> OrderDiscountResponse.from(d, names.get(d.getCreatedById())))
              .toList();
      responses.add(OrderResponse.from(order, items, payments, discounts));
    }
    return responses;
  }

  /**
   * "Nombres reales" (mejora pedida por Fran para la caja, aplicada acá
   * también para que un pedido con cobros muestre lo mismo) — un solo
   * lookup en lote, ver {@code CashSessionService#resolveNames} para el
   * mismo criterio.
   */
  private Map<String, String> resolveUserNames(Set<String> userIds) {
    Map<String, String> names = new HashMap<>();
    if (userIds.isEmpty()) return names;
    for (User user : userRepository.findAllByIdInScoped(userIds)) {
      names.put(user.getId(), user.getFirstName() + " " + user.getLastName());
    }
    return names;
  }
}
