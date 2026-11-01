package com.cofeapp.backend.dashboard.dto;

import com.cofeapp.backend.domain.enums.PaymentMethod;
import com.cofeapp.backend.stock.dto.IngredientStockLevelResponse;
import com.cofeapp.backend.stock.dto.ProductStockLevelResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Resumen de KPIs de UN local, en el rango {@code [from, to)} pedido —
 * Bloque E (ítem "dashboard con facturación/estadísticas/KPI"). Ver el
 * Javadoc de {@code DashboardService#summary} sobre de dónde sale cada
 * número. {@code lowIngredients`/`lowProducts} reutilizan tal cual los DTOs
 * del Bloque D (Stock) — ya traen {@code belowMinimum} calculado, acá solo
 * se filtran los que están por debajo del umbral (la alerta en sí).
 */
public record DashboardSummaryResponse(
    String locationId,
    Instant from,
    Instant to,
    BigDecimal totalSales,
    long orderCount,
    BigDecimal averageTicket,
    List<TopProductResponse> topProducts,
    Map<PaymentMethod, BigDecimal> salesByMethod,
    List<IngredientStockLevelResponse> lowIngredients,
    List<ProductStockLevelResponse> lowProducts) {}
