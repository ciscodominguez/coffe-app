package com.cofeapp.backend.report;

import com.cofeapp.backend.common.exception.BadRequestException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reporte en Excel para el contador (paso 2.6). Mismos roles que el panel
 * de Inicio ({@code DashboardController}): dueño, administrador y
 * supervisor.
 */
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

  private static final MediaType XLSX =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final ReportService reportService;

  /**
   * {@code from}/{@code to}: instantes ISO-8601 (el frontend manda el
   * principio y el final de los días elegidos en la hora del café).
   * {@code locationId}: opcional, sin él son todos los locales. {@code tz}:
   * zona horaria para mostrar las fechas (por defecto, Argentina).
   */
  @GetMapping("/excel")
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN', 'SUPERVISOR')")
  public ResponseEntity<byte[]> excel(
      @RequestParam String from,
      @RequestParam String to,
      @RequestParam(required = false) String locationId,
      @RequestParam(required = false) String tz) {
    ReportService.ExcelReport report =
        reportService.excel(
            locationId == null || locationId.isBlank() ? null : locationId,
            parseInstant(from, "desde"),
            parseInstant(to, "hasta"),
            ReportService.parseZone(tz));
    return ResponseEntity.ok()
        .contentType(XLSX)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(report.fileName()).build().toString())
        .body(report.content());
  }

  private static Instant parseInstant(String value, String name) {
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException e) {
      throw new BadRequestException("La fecha \"" + name + "\" no es válida.");
    }
  }
}
