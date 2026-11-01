package com.cofeapp.backend.dashboard;

import com.cofeapp.backend.common.exception.BadRequestException;
import com.cofeapp.backend.dashboard.dto.DashboardSummaryResponse;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bloque E — reservado a gerencia (OWNER/ADMIN/SUPERVISOR), a diferencia de
 * Productos/Insumos que son de solo-lectura para todos: acá se ve
 * facturación real del negocio, no algo que un mesero/cocina necesite para
 * atender.
 *
 * <p>{@code from}/{@code to} viajan como {@code String} (no {@code
 * Instant} tipado en la firma) para controlar a mano el mensaje de error
 * si el frontend manda algo mal formado, en vez de depender del formato
 * exacto que registre por default el conversor de Spring para {@code
 * Instant} en query params.
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

  private final DashboardService dashboardService;

  @GetMapping("/summary")
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN', 'SUPERVISOR')")
  public DashboardSummaryResponse summary(
      @RequestParam String locationId, @RequestParam String from, @RequestParam String to) {
    return dashboardService.summary(locationId, parseInstant(from, "from"), parseInstant(to, "to"));
  }

  private Instant parseInstant(String value, String paramName) {
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException e) {
      throw new BadRequestException(
          "\"" + paramName + "\" tiene que ser una fecha ISO-8601 válida (ej. 2026-09-19T00:00:00Z).");
    }
  }
}
