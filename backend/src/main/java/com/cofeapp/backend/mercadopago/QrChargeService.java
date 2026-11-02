package com.cofeapp.backend.mercadopago;

import com.cofeapp.backend.cash.CashSessionRepository;
import com.cofeapp.backend.common.context.TenantContext;
import com.cofeapp.backend.common.exception.BadRequestException;
import com.cofeapp.backend.common.exception.ConflictException;
import com.cofeapp.backend.common.exception.NotFoundException;
import com.cofeapp.backend.domain.Location;
import com.cofeapp.backend.domain.MercadoPagoAccount;
import com.cofeapp.backend.domain.MpQrCharge;
import com.cofeapp.backend.domain.enums.CashSessionStatus;
import com.cofeapp.backend.domain.enums.OrderStatus;
import com.cofeapp.backend.domain.enums.QrChargeStatus;
import com.cofeapp.backend.domain.enums.QrMode;
import com.cofeapp.backend.location.LocationRepository;
import com.cofeapp.backend.mercadopago.client.MercadoPagoApiException;
import com.cofeapp.backend.mercadopago.client.MercadoPagoClient;
import com.cofeapp.backend.mercadopago.client.MercadoPagoClient.MpOrder;
import com.cofeapp.backend.mercadopago.client.MercadoPagoClient.MpQrOrderRequest;
import com.cofeapp.backend.mercadopago.dto.CreateQrChargeRequest;
import com.cofeapp.backend.mercadopago.dto.QrChargeResponse;
import com.cofeapp.backend.order.OrderService;
import com.cofeapp.backend.order.OrderService.QrPaymentResult;
import com.cofeapp.backend.order.dto.OrderResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cobro de un pedido con QR de Mercado Pago (V10).
 *
 * <p><b>Flujo:</b>
 * <ol>
 *   <li>Quien cobra toca "QR Mercado Pago" → {@link #create}: se crea una
 *       orden en Mercado Pago por el saldo del pedido (o por el monto
 *       elegido) y queda un cobro PENDING.
 *       <ul>
 *         <li>Modo DYNAMIC: MP devuelve el texto del QR y la pantalla lo
 *             dibuja.
 *         <li>Modo STATIC: el monto se "carga" en el QR impreso del
 *             mostrador; el cliente escanea ese.
 *       </ul>
 *   <li>El cliente paga desde su app de Mercado Pago.
 *   <li>Nos enteramos por {@link #sync}, que corre desde tres lados: la
 *       ventana de cobro pregunta cada pocos segundos ({@link #get}), un
 *       proceso de fondo revisa los pendientes ({@link #syncAllPending}) y,
 *       con la app publicada, Mercado Pago avisa por webhook ({@link
 *       #handleWebhook}). En los tres casos se le vuelve a preguntar el
 *       estado a Mercado Pago: nunca se confía en lo que dice un aviso.
 *   <li>Si MP dice "processed", se registra el cobro normal del pedido
 *       (método QR) en la caja abierta — ver {@link
 *       OrderService#registerConfirmedQrPayment}.
 * </ol>
 *
 * <p><b>Una sola vez:</b> {@link #apply} bloquea la fila del cobro
 * ({@code SELECT ... FOR UPDATE}) y solo actúa si sigue PENDING, así que
 * aunque la pantalla, el proceso de fondo y el webhook lleguen juntos, el
 * cobro se registra una única vez.
 */
@Slf4j
@Service
public class QrChargeService {

  private final MpQrChargeRepository chargeRepository;
  private final MercadoPagoAccountRepository accountRepository;
  private final MercadoPagoAccountService accountService;
  private final LocationRepository locationRepository;
  private final CashSessionRepository cashSessionRepository;
  private final OrderService orderService;
  private final MercadoPagoClient mercadoPagoClient;
  private final MercadoPagoProperties properties;
  private final TenantContext tenantContext;
  private final TransactionTemplate transactionTemplate;

  @PersistenceContext private EntityManager entityManager;

  public QrChargeService(
      MpQrChargeRepository chargeRepository,
      MercadoPagoAccountRepository accountRepository,
      MercadoPagoAccountService accountService,
      LocationRepository locationRepository,
      CashSessionRepository cashSessionRepository,
      OrderService orderService,
      MercadoPagoClient mercadoPagoClient,
      MercadoPagoProperties properties,
      TenantContext tenantContext,
      PlatformTransactionManager transactionManager) {
    this.chargeRepository = chargeRepository;
    this.accountRepository = accountRepository;
    this.accountService = accountService;
    this.locationRepository = locationRepository;
    this.cashSessionRepository = cashSessionRepository;
    this.orderService = orderService;
    this.mercadoPagoClient = mercadoPagoClient;
    this.properties = properties;
    this.tenantContext = tenantContext;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  /**
   * Arranca un cobro con QR. Si el pedido ya tiene uno esperando, devuelve
   * ese (no crea otro): así, si se cierra y se vuelve a abrir la ventana de
   * cobro, aparece el mismo QR.
   */
  public QrChargeResponse create(String orderId, CreateQrChargeRequest request) {
    OrderResponse order = orderService.findOne(orderId);
    if (order.status() == OrderStatus.DELIVERED || order.status() == OrderStatus.CANCELLED) {
      throw new ConflictException("No se pueden registrar cobros en un pedido ya cerrado.");
    }

    Optional<MpQrCharge> pending =
        chargeRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(orderId, QrChargeStatus.PENDING);
    if (pending.isPresent()) {
      return toResponse(pending.get());
    }

    MercadoPagoAccount account = accountService.requireAccount();
    Location location =
        locationRepository
            .findByIdScoped(order.locationId())
            .orElseThrow(() -> new NotFoundException("No se encontró el local del pedido."));
    if (location.getMpPosExternalId() == null) {
      throw new ConflictException("Falta configurar el QR de este local en \"Cobros con QR\".");
    }
    if (!cashSessionRepository.existsByLocationIdAndStatus(location.getId(), CashSessionStatus.OPEN)) {
      throw new ConflictException("Abrí la caja del local antes de registrar un cobro.");
    }

    BigDecimal balanceDue = order.balanceDue();
    if (balanceDue.signum() <= 0) {
      throw new ConflictException("El pedido ya está cobrado por completo.");
    }
    // V14 — "por productos": el monto sale de los productos elegidos (validados igual que en un cobro común).
    boolean byItems = request != null && request.getItems() != null && !request.getItems().isEmpty();
    BigDecimal amount =
        byItems
            ? orderService.quoteItemsPayment(order.id(), request.getItems())
            : request != null && request.getAmount() != null ? request.getAmount() : balanceDue;
    if (amount.signum() <= 0) {
      throw new BadRequestException("El monto tiene que ser mayor a 0.");
    }
    if (amount.compareTo(balanceDue) > 0) {
      throw new ConflictException("Ese monto supera el saldo pendiente del pedido ($" + balanceDue.toPlainString() + ").");
    }

    QrMode mode = location.getMpQrMode();
    if (mode == QrMode.STATIC
        && chargeRepository
            .findFirstByLocationIdAndModeAndStatus(location.getId(), QrMode.STATIC, QrChargeStatus.PENDING)
            .isPresent()) {
      throw new ConflictException(
          "El QR del mostrador ya está esperando el pago de otro pedido. Esperá a que se pague o cancelá ese cobro.");
    }

    String chargeId = UUID.randomUUID().toString();
    int minutes = properties.chargeExpirationMinutes();
    String description = "Pedido #" + order.id().substring(0, 8).toUpperCase();
    MpOrder mpOrder =
        mercadoPagoClient.createQrOrder(
            accountService.accessTokenOf(account),
            chargeId,
            new MpQrOrderRequest(
                chargeId,
                location.getMpPosExternalId(),
                mode == QrMode.STATIC ? "static" : "dynamic",
                amount,
                description,
                "PT" + minutes + "M"));

    MpQrCharge charge = new MpQrCharge();
    charge.setId(chargeId);
    charge.setLocationId(location.getId());
    charge.setOrderId(order.id());
    charge.setMode(mode);
    charge.setAmount(amount);
    charge.setPaidItems(byItems ? OrderService.encodePaidItems(request.getItems()) : null);
    charge.setStatus(QrChargeStatus.PENDING);
    charge.setMpOrderId(mpOrder.id());
    charge.setQrData(mpOrder.qrData());
    charge.setMpStatus(mpOrder.status());
    charge.setMpStatusDetail(mpOrder.statusDetail());
    charge.setCreatedById(tenantContext.getUserId());
    charge.setExpiresAt(Instant.now().plus(Duration.ofMinutes(minutes)));
    // El id ya viene puesto (es el external_reference que viajó a MP), así
    // que save() hace merge y devuelve OTRA instancia: esa es la que tiene
    // businessId y fechas.
    charge = chargeRepository.save(charge);
    return toResponse(charge);
  }

  /** Estado actual; si sigue esperando, antes le pregunta a Mercado Pago. */
  public QrChargeResponse get(String chargeId) {
    MpQrCharge charge = findOneOrThrow(chargeId);
    if (charge.getStatus() == QrChargeStatus.PENDING) {
      sync(charge);
      charge = reload(chargeId);
    }
    return toResponse(charge);
  }

  /**
   * Cancela un cobro que sigue esperando. Si justo el cliente ya había
   * pagado, Mercado Pago no deja cancelar y el cobro queda PAID: la
   * respuesta lo muestra.
   */
  public QrChargeResponse cancel(String chargeId) {
    MpQrCharge charge = findOneOrThrow(chargeId);
    if (charge.getStatus() != QrChargeStatus.PENDING) {
      return toResponse(charge);
    }
    String token = accountService.accessTokenOf(accountService.requireAccount());
    String cancelError = null;
    try {
      MpOrder result = mercadoPagoClient.cancelOrder(token, charge.getMpOrderId(), "cancel-" + charge.getId());
      apply(charge.getId(), result);
    } catch (MercadoPagoApiException ex) {
      cancelError = ex.getMessage();
    }
    MpQrCharge current = reload(chargeId);
    if (current.getStatus() == QrChargeStatus.PENDING) {
      sync(current);
      current = reload(chargeId);
    }
    if (current.getStatus() == QrChargeStatus.PENDING) {
      throw new ConflictException(cancelError != null ? cancelError : "Mercado Pago todavía no confirmó la cancelación. Probá de nuevo.");
    }
    return toResponse(current);
  }

  /**
   * Proceso de fondo: revisa los cobros que siguen esperando, de todos los
   * negocios (corre sin sesión, por eso las consultas no filtran por
   * negocio — ver {@link QrChargeSweeper}).
   */
  public void syncAllPending() {
    List<MpQrCharge> pending = chargeRepository.findTop50ByStatusOrderByCreatedAtAsc(QrChargeStatus.PENDING);
    for (MpQrCharge charge : pending) {
      try {
        sync(charge);
      } catch (RuntimeException ex) {
        log.warn("No se pudo revisar el cobro con QR {}: {}", charge.getId(), ex.getMessage());
      }
    }
  }

  /**
   * Aviso de Mercado Pago (webhook). Solo trae el id de SU orden: se busca
   * el cobro y se consulta el estado real con el token del negocio. Si el
   * id no es nuestro, no se hace nada.
   */
  public void handleWebhook(String mpOrderId) {
    if (mpOrderId == null || mpOrderId.isBlank()) return;
    chargeRepository
        .findByMpOrderId(mpOrderId)
        .filter(c -> c.getStatus() == QrChargeStatus.PENDING)
        .ifPresent(this::sync);
  }

  // ---------------------------------------------------------------------

  /** Le pregunta a Mercado Pago por el cobro y aplica lo que responda. Los errores de red dejan el cobro como estaba. */
  private void sync(MpQrCharge charge) {
    Optional<MercadoPagoAccount> account = accountRepository.findByBusinessId(charge.getBusinessId());
    if (account.isEmpty() || charge.getMpOrderId() == null) return;
    MpOrder mpOrder;
    try {
      mpOrder = mercadoPagoClient.getOrder(accountService.accessTokenOf(account.get()), charge.getMpOrderId());
    } catch (MercadoPagoApiException ex) {
      log.warn("No se pudo consultar el cobro con QR {} en Mercado Pago: {}", charge.getId(), ex.getMessage());
      return;
    }
    apply(charge.getId(), mpOrder);
  }

  /** Aplica el estado de Mercado Pago con la fila bloqueada — ver el Javadoc de la clase. */
  private void apply(String chargeId, MpOrder mpOrder) {
    transactionTemplate.executeWithoutResult(
        tx -> {
          MpQrCharge charge = entityManager.find(MpQrCharge.class, chargeId);
          if (charge == null) return;
          // refresh: relee de la base (no del caché de esta request) Y bloquea la fila.
          entityManager.refresh(charge, LockModeType.PESSIMISTIC_WRITE);
          if (charge.getStatus() != QrChargeStatus.PENDING) return;

          String mpStatus = mpOrder.status() == null ? "" : mpOrder.status();
          charge.setMpStatus(mpOrder.status());
          charge.setMpStatusDetail(mpOrder.statusDetail());
          switch (mpStatus) {
            case "processed" -> {
              QrPaymentResult result = orderService.registerConfirmedQrPayment(charge);
              if (result.registered()) {
                charge.setStatus(QrChargeStatus.PAID);
                charge.setOrderPaymentId(result.paymentId());
                charge.setStatusMessage(null);
              } else {
                charge.setStatus(QrChargeStatus.PAID_UNAPPLIED);
                charge.setStatusMessage(
                    "Mercado Pago cobró $" + charge.getAmount().toPlainString() + " pero no se pudo registrar solo: "
                        + result.problem() + " Registralo a mano.");
                log.warn("Cobro con QR {} pagado pero no registrado: {}", charge.getId(), result.problem());
              }
            }
            case "expired" -> {
              charge.setStatus(QrChargeStatus.EXPIRED);
              charge.setStatusMessage("El QR venció sin que se pagara.");
            }
            case "canceled", "cancelled" -> {
              charge.setStatus(QrChargeStatus.CANCELLED);
              charge.setStatusMessage("Cobro cancelado.");
            }
            case "failed", "refunded" -> {
              charge.setStatus(QrChargeStatus.FAILED);
              charge.setStatusMessage("Mercado Pago no aprobó el pago.");
            }
            default -> {
              // created / action_required / at_terminal…: sigue esperando.
            }
          }
          if (charge.getStatus() != QrChargeStatus.PENDING) {
            charge.setResolvedAt(Instant.now());
          }
        });
  }

  private MpQrCharge findOneOrThrow(String chargeId) {
    return chargeRepository
        .findByIdScoped(chargeId)
        .orElseThrow(() -> new NotFoundException("No se encontró el cobro con QR " + chargeId + "."));
  }

  /** Vuelve a leer de la base (lo que haya escrito {@link #apply}), no del caché de la request. */
  private MpQrCharge reload(String chargeId) {
    // refresh() exige una transacción abierta (con el EntityManager de la request).
    return transactionTemplate.execute(
        tx -> {
          MpQrCharge charge = findOneOrThrow(chargeId);
          entityManager.refresh(charge);
          return charge;
        });
  }

  private QrChargeResponse toResponse(MpQrCharge charge) {
    String qrImageUrl = null;
    if (charge.getMode() == QrMode.STATIC) {
      qrImageUrl = locationRepository.findByIdScoped(charge.getLocationId()).map(Location::getMpQrImageUrl).orElse(null);
    }
    return QrChargeResponse.from(charge, qrImageUrl, orderService.findOne(charge.getOrderId()));
  }
}
