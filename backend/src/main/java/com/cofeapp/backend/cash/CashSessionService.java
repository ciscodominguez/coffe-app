package com.cofeapp.backend.cash;

import com.cofeapp.backend.cash.dto.CashMovementResponse;
import com.cofeapp.backend.cash.dto.CashSessionResponse;
import com.cofeapp.backend.cash.dto.CloseCashSessionRequest;
import com.cofeapp.backend.cash.dto.CreateCashMovementRequest;
import com.cofeapp.backend.cash.dto.DenominationCountLine;
import com.cofeapp.backend.cash.dto.DenominationCountResponse;
import com.cofeapp.backend.cash.dto.OpenCashSessionRequest;
import com.cofeapp.backend.common.context.TenantContext;
import com.cofeapp.backend.common.exception.BadRequestException;
import com.cofeapp.backend.common.exception.ConflictException;
import com.cofeapp.backend.common.exception.NotFoundException;
import com.cofeapp.backend.domain.CashMovement;
import com.cofeapp.backend.domain.CashSession;
import com.cofeapp.backend.domain.CashSessionDenomination;
import com.cofeapp.backend.domain.OrderPayment;
import com.cofeapp.backend.domain.User;
import com.cofeapp.backend.domain.enums.CashMovementType;
import com.cofeapp.backend.domain.enums.CashSessionStatus;
import com.cofeapp.backend.domain.enums.PaymentMethod;
import com.cofeapp.backend.domain.enums.QrChargeStatus;
import com.cofeapp.backend.location.LocationRepository;
import com.cofeapp.backend.mercadopago.MpQrChargeRepository;
import com.cofeapp.backend.order.OrderPaymentRepository;
import com.cofeapp.backend.order.dto.OrderPaymentResponse;
import com.cofeapp.backend.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turnos de caja — ítem 14 de las mejoras pedidas. Tres operaciones:
 *
 * <ol>
 *   <li>{@link #open}: abre un turno nuevo para un local, con un fondo
 *       inicial. Un solo turno OPEN por local a la vez — garantizado a
 *       nivel de base (ver el Javadoc de {@link CashSession}), acá además
 *       se chequea antes para devolver un mensaje claro en el caso común.
 *   <li>{@link #addMovement}: carga un ingreso/egreso manual de efectivo
 *       sobre un turno abierto (ítem 14).
 *   <li>{@link #close}: cierra el turno, registrando lo contado a mano y
 *       (ítem 15) el lote de Postnet del cierre.
 * </ol>
 *
 * <p>Los totales (ventas por método, efectivo esperado, diferencia) NUNCA
 * se guardan — se recalculan en cada lectura a partir de {@code
 * order_payments}/{@code cash_movements} (ver {@link #toResponse}), tanto
 * para un turno abierto como ya cerrado.
 *
 * <p>"Caja más profesional" (mejoras pedidas por Fran después del Bloque C
 * original): {@link #toResponse} ahora también resuelve nombres reales
 * (ver {@link #resolveNames}) y arma el detalle línea por línea de cobros
 * del turno; {@link #close} acepta un conteo de efectivo por billete
 * opcional (ver {@link #assertKnownDenomination}).
 */
@Service
@RequiredArgsConstructor
public class CashSessionService {

  /**
   * Billetes vigentes en Argentina (confirmado vía BCRA/prensa, septiembre
   * 2026) — las monedas y los billetes de $50 o menos quedan afuera a
   * propósito: por la devaluación son inviables para contar en una caja de
   * café real. Si el BCRA emite una denominación nueva, agregarla acá (y
   * al CHECK de la migración V5).
   */
  private static final List<BigDecimal> KNOWN_DENOMINATIONS =
      List.of(
          new BigDecimal("20000"),
          new BigDecimal("10000"),
          new BigDecimal("2000"),
          new BigDecimal("1000"),
          new BigDecimal("500"),
          new BigDecimal("200"),
          new BigDecimal("100"),
          new BigDecimal("50"),
          new BigDecimal("20"),
          new BigDecimal("10"));

  private final CashSessionRepository cashSessionRepository;
  private final CashMovementRepository cashMovementRepository;
  private final CashSessionDenominationRepository cashSessionDenominationRepository;
  private final OrderPaymentRepository orderPaymentRepository;
  private final MpQrChargeRepository qrChargeRepository;
  private final LocationRepository locationRepository;
  private final UserRepository userRepository;
  private final TenantContext tenantContext;

  public List<CashSessionResponse> findAll() {
    return cashSessionRepository.findAllByOrderByOpenedAtDesc().stream().map(this::toResponse).toList();
  }

  public List<CashSessionResponse> findAllByLocation(String locationId) {
    assertLocationExists(locationId);
    return cashSessionRepository.findAllByLocationIdOrderByOpenedAtDesc(locationId).stream()
        .map(this::toResponse)
        .toList();
  }

  /** El turno abierto de un local, si hay uno — lo usa el frontend para saber si puede cobrar o tiene que abrir caja primero. */
  public CashSessionResponse findCurrent(String locationId) {
    assertLocationExists(locationId);
    CashSession session =
        cashSessionRepository
            .findByLocationIdAndStatus(locationId, CashSessionStatus.OPEN)
            .orElseThrow(() -> new NotFoundException("No hay un turno de caja abierto en ese local."));
    return toResponse(session);
  }

  public CashSessionResponse findOne(String id) {
    return toResponse(findOneOrThrow(id));
  }

  @Transactional
  public CashSessionResponse open(OpenCashSessionRequest request) {
    assertLocationExists(request.getLocationId());
    if (cashSessionRepository.existsByLocationIdAndStatus(request.getLocationId(), CashSessionStatus.OPEN)) {
      throw new ConflictException("Ya hay un turno de caja abierto en ese local.");
    }

    CashSession session = new CashSession();
    session.setLocationId(request.getLocationId());
    session.setStatus(CashSessionStatus.OPEN);
    session.setOpenedById(tenantContext.getUserId());
    session.setOpenedAt(Instant.now());
    session.setOpeningAmount(request.getOpeningAmount());
    session.setNotes(request.getNotes());

    try {
      session = cashSessionRepository.save(session);
    } catch (DataIntegrityViolationException e) {
      // Dos requests concurrentes abriendo el mismo local: la que perdió la
      // carrera contra el UNIQUE KEY (business_id, open_location_id) de la
      // migración cae acá — mismo criterio que la idempotencia de
      // OrderService#create.
      throw new ConflictException("Ya hay un turno de caja abierto en ese local.");
    }

    return toResponse(session);
  }

  @Transactional
  public CashMovementResponse addMovement(String cashSessionId, CreateCashMovementRequest request) {
    CashSession session = findOneOrThrow(cashSessionId);
    assertOpen(session);

    CashMovement movement = new CashMovement();
    movement.setCashSessionId(session.getId());
    movement.setType(request.getType());
    movement.setAmount(request.getAmount());
    movement.setReason(request.getReason());
    movement.setCreatedById(tenantContext.getUserId());

    String createdByName = resolveNames(Set.of(movement.getCreatedById())).get(movement.getCreatedById());
    return CashMovementResponse.from(cashMovementRepository.save(movement), createdByName);
  }

  /**
   * Cierra el turno. Si {@code request.denominationBreakdown} viene con
   * líneas ("conteo de efectivo por billete" — mejora "caja más
   * profesional"), el {@code countedCashAmount} efectivamente guardado se
   * RECALCULA como la suma de ese desglose (ignorando el valor suelto que
   * mande el cliente): así el número que se compara contra lo esperado
   * siempre coincide con lo que la persona realmente contó billete por
   * billete, nunca puede quedar desincronizado por un typo en el campo
   * "total". Sin desglose, sigue funcionando como antes (un único monto).
   */
  @Transactional
  public CashSessionResponse close(String cashSessionId, CloseCashSessionRequest request) {
    CashSession session = findOneOrThrow(cashSessionId);
    assertOpen(session);
    if (qrChargeRepository.existsByLocationIdAndStatus(session.getLocationId(), QrChargeStatus.PENDING)) {
      // Si se cerrara ahora y el cliente paga el QR después, ese cobro no
      // tendría caja donde registrarse.
      throw new ConflictException(
          "Hay un cobro con QR de Mercado Pago esperando el pago en este local. Esperá a que se confirme o"
              + " cancelalo antes de cerrar la caja.");
    }

    BigDecimal countedCashAmount = request.getCountedCashAmount();
    List<DenominationCountLine> breakdown = request.getDenominationBreakdown();
    if (breakdown != null && !breakdown.isEmpty()) {
      countedCashAmount = saveDenominationBreakdown(session, breakdown);
    }

    session.setStatus(CashSessionStatus.CLOSED);
    session.setClosedById(tenantContext.getUserId());
    session.setClosedAt(Instant.now());
    session.setCountedCashAmount(countedCashAmount);
    session.setPostnetBatchNumber(request.getPostnetBatchNumber());
    session.setPostnetReportedAmount(request.getPostnetReportedAmount());
    if (request.getNotes() != null) {
      session.setNotes(request.getNotes());
    }

    return toResponse(cashSessionRepository.save(session));
  }

  /** Valida, persiste y totaliza el conteo por billete — ver el Javadoc de {@link #close}. */
  private BigDecimal saveDenominationBreakdown(CashSession session, List<DenominationCountLine> breakdown) {
    Set<BigDecimal> seen = new HashSet<>();
    BigDecimal total = BigDecimal.ZERO;
    List<CashSessionDenomination> lines = new ArrayList<>();
    for (DenominationCountLine line : breakdown) {
      assertKnownDenomination(line.getDenomination());
      if (!seen.add(line.getDenomination())) {
        throw new BadRequestException(
            "El billete de $" + line.getDenomination().toPlainString() + " está repetido en el conteo.");
      }
      BigDecimal subtotal = line.getDenomination().multiply(BigDecimal.valueOf(line.getQuantity()));
      total = total.add(subtotal);

      CashSessionDenomination entity = new CashSessionDenomination();
      entity.setCashSessionId(session.getId());
      entity.setDenomination(line.getDenomination());
      entity.setQuantity(line.getQuantity());
      entity.setSubtotal(subtotal);
      lines.add(entity);
    }
    cashSessionDenominationRepository.saveAll(lines);
    return total;
  }

  private void assertKnownDenomination(BigDecimal denomination) {
    boolean known = KNOWN_DENOMINATIONS.stream().anyMatch(value -> value.compareTo(denomination) == 0);
    if (!known) {
      throw new BadRequestException(
          "$"
              + denomination.toPlainString()
              + " no es un billete válido. Valores aceptados: "
              + KNOWN_DENOMINATIONS.stream().map(BigDecimal::toPlainString).reduce((a, b) -> a + ", " + b).orElse("")
              + ".");
    }
  }

  private void assertOpen(CashSession session) {
    if (session.getStatus() != CashSessionStatus.OPEN) {
      throw new ConflictException("Ese turno de caja ya está cerrado.");
    }
  }

  private void assertLocationExists(String locationId) {
    locationRepository
        .findByIdScoped(locationId)
        .orElseThrow(() -> new NotFoundException("No se encontró el local " + locationId + "."));
  }

  private CashSession findOneOrThrow(String id) {
    return cashSessionRepository
        .findByIdScoped(id)
        .orElseThrow(() -> new NotFoundException("No se encontró el turno de caja " + id + "."));
  }

  private CashSessionResponse toResponse(CashSession session) {
    List<OrderPayment> orderPayments =
        orderPaymentRepository.findAllByCashSessionIdOrderByCreatedAtAsc(session.getId());
    Map<PaymentMethod, BigDecimal> salesByMethod = new EnumMap<>(PaymentMethod.class);
    for (OrderPayment payment : orderPayments) {
      salesByMethod.merge(payment.getPaymentMethod(), payment.getAmount(), BigDecimal::add);
    }

    BigDecimal incomeTotal = sumMovements(session.getId(), CashMovementType.INCOME);
    BigDecimal expenseTotal = sumMovements(session.getId(), CashMovementType.EXPENSE);

    List<CashMovement> movementEntities = cashMovementRepository.findAllByCashSessionIdOrderByCreatedAtAsc(session.getId());
    List<DenominationCountResponse> denominationBreakdown =
        cashSessionDenominationRepository.findAllByCashSessionIdOrderByDenominationDesc(session.getId()).stream()
            .map(DenominationCountResponse::from)
            .toList();

    // "Nombres reales" (mejora pedida por Fran): un solo lookup en lote
    // para TODOS los ids de usuario que aparecen en el turno (quien lo
    // abrió/cerró + quien cargó cada movimiento/cobro), no uno por fila.
    Set<String> userIds = new HashSet<>();
    userIds.add(session.getOpenedById());
    if (session.getClosedById() != null) userIds.add(session.getClosedById());
    movementEntities.forEach(m -> userIds.add(m.getCreatedById()));
    orderPayments.forEach(p -> userIds.add(p.getCreatedById()));
    Map<String, String> names = resolveNames(userIds);

    List<CashMovementResponse> movements =
        movementEntities.stream().map(m -> CashMovementResponse.from(m, names.get(m.getCreatedById()))).toList();
    List<OrderPaymentResponse> payments =
        orderPayments.stream()
            .map(p -> OrderPaymentResponse.from(p, names.get(p.getCreatedById())))
            .toList();

    return CashSessionResponse.from(
        session,
        names.get(session.getOpenedById()),
        session.getClosedById() == null ? null : names.get(session.getClosedById()),
        salesByMethod,
        incomeTotal,
        expenseTotal,
        movements,
        payments,
        denominationBreakdown);
  }

  /** "Nombres reales, no solo totales" — resuelve varios ids de usuario en una sola query. */
  private Map<String, String> resolveNames(Set<String> userIds) {
    Map<String, String> names = new HashMap<>();
    for (User user : userRepository.findAllByIdInScoped(userIds)) {
      names.put(user.getId(), user.getFirstName() + " " + user.getLastName());
    }
    return names;
  }

  private BigDecimal sumMovements(String cashSessionId, CashMovementType type) {
    return cashMovementRepository.findAllByCashSessionIdAndType(cashSessionId, type).stream()
        .map(CashMovement::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
