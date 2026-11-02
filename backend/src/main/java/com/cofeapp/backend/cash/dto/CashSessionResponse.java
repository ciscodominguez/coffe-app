package com.cofeapp.backend.cash.dto;

import com.cofeapp.backend.domain.CashSession;
import com.cofeapp.backend.domain.enums.CashSessionStatus;
import com.cofeapp.backend.domain.enums.PaymentMethod;
import com.cofeapp.backend.order.dto.OrderPaymentResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Detalle de un turno de caja con los totales YA CALCULADOS — nunca se
 * persisten (se recalculan en cada lectura a partir de {@code
 * order_payments}/{@code cash_movements}, ver {@code
 * CashSessionService#toResponse}), así nunca pueden quedar desincronizados
 * de los pagos/movimientos reales.
 *
 * <p>{@code expectedCashAmount} = fondo inicial + cobros en efectivo del
 * turno + ingresos manuales − egresos manuales. {@code cashDifference} =
 * {@code countedCashAmount − expectedCashAmount}, nulo mientras el turno
 * sigue abierto (todavía no se contó nada). Positivo = sobra plata,
 * negativo = falta.
 *
 * <p>Mejoras "caja más profesional" pedidas por Fran:
 *
 * <ul>
 *   <li>{@code openedByName}/{@code closedByName} y {@code
 *       createdByName} en cada {@link CashMovementResponse}/{@link
 *       OrderPaymentResponse} de {@code payments} — "nombres reales, no
 *       solo totales".
 *   <li>{@code payments} — el detalle línea por línea de qué pedidos se
 *       cobraron en el turno (antes solo existía el agregado {@code
 *       salesByMethod}).
 *   <li>{@code denominationBreakdown} — "conteo de efectivo por billete",
 *       ver {@code CashSessionDenomination}. Vacío en un turno cerrado
 *       "a la vieja" (sin desglose) o mientras sigue abierto.
 * </ul>
 */
public record CashSessionResponse(
    String id,
    String locationId,
    CashSessionStatus status,
    String openedById,
    String openedByName,
    Instant openedAt,
    BigDecimal openingAmount,
    String closedById,
    String closedByName,
    Instant closedAt,
    BigDecimal countedCashAmount,
    String postnetBatchNumber,
    BigDecimal postnetReportedAmount,
    String notes,
    Instant createdAt,
    Instant updatedAt,
    Map<PaymentMethod, BigDecimal> salesByMethod,
    BigDecimal totalSales,
    BigDecimal cashIncomeMovementsTotal,
    BigDecimal cashExpenseMovementsTotal,
    BigDecimal expectedCashAmount,
    BigDecimal cashDifference,
    List<CashMovementResponse> movements,
    List<OrderPaymentResponse> payments,
    List<DenominationCountResponse> denominationBreakdown) {

  public static CashSessionResponse from(
      CashSession session,
      String openedByName,
      String closedByName,
      Map<PaymentMethod, BigDecimal> salesByMethod,
      BigDecimal cashIncomeMovementsTotal,
      BigDecimal cashExpenseMovementsTotal,
      List<CashMovementResponse> movements,
      List<OrderPaymentResponse> payments,
      List<DenominationCountResponse> denominationBreakdown) {
    BigDecimal totalSales = salesByMethod.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal cashSales = salesByMethod.getOrDefault(PaymentMethod.CASH, BigDecimal.ZERO);
    BigDecimal expectedCashAmount =
        session
            .getOpeningAmount()
            .add(cashSales)
            .add(cashIncomeMovementsTotal)
            .subtract(cashExpenseMovementsTotal);
    BigDecimal cashDifference =
        session.getCountedCashAmount() == null
            ? null
            : session.getCountedCashAmount().subtract(expectedCashAmount);

    return new CashSessionResponse(
        session.getId(),
        session.getLocationId(),
        session.getStatus(),
        session.getOpenedById(),
        openedByName,
        session.getOpenedAt(),
        session.getOpeningAmount(),
        session.getClosedById(),
        closedByName,
        session.getClosedAt(),
        session.getCountedCashAmount(),
        session.getPostnetBatchNumber(),
        session.getPostnetReportedAmount(),
        session.getNotes(),
        session.getCreatedAt(),
        session.getUpdatedAt(),
        salesByMethod,
        totalSales,
        cashIncomeMovementsTotal,
        cashExpenseMovementsTotal,
        expectedCashAmount,
        cashDifference,
        movements,
        payments,
        denominationBreakdown);
  }
}
