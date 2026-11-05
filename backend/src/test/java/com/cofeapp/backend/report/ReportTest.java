package com.cofeapp.backend.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.cofeapp.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Reporte en Excel para el contador (paso 2.6). */
@DisplayName("Reporte en Excel")
class ReportTest extends IntegrationTest {

  @Test
  @DisplayName("Un solo Excel con Resumen, Ventas, Productos, Cierres de caja y Descuentos, con los datos del período")
  void fullReport() throws Exception {
    Tenant cafe = newBusiness();
    Staff cocina = newStaff(cafe, "KITCHEN");
    String latte = newProduct(cafe, "Latte", "3000");
    String medialuna = newProduct(cafe, "Medialuna", "700");
    String caja = openCash(cafe.token(), cafe.locationId(), "5000");

    JsonNode order = createOrder(cafe.token(), cafe.locationId(), null, item(latte, 2), item(medialuna, 3));
    Map<String, Object> discount = new HashMap<>();
    discount.put("type", "PERCENTAGE");
    discount.put("value", new BigDecimal("10"));
    discount.put("reason", "Cliente frecuente & amigo <3");
    post(cafe.token(), "/orders/" + id(order) + "/discounts", discount, 200); // 8100 − 810 = 7290
    pay(cafe.token(), id(order), "CASH", "4000", 201);
    pay(cafe.token(), id(order), "DEBIT_CARD", "3290", 201);
    deliver(cafe, cocina, id(order));

    // Un pedido sin entregar no es venta todavía.
    createOrder(cafe.token(), cafe.locationId(), null, item(latte, 1));

    post(cafe.token(), "/cash-sessions/" + caja + "/close", Map.of("countedCashAmount", 9000), 200);

    Map<String, String> sheets = download(cafe.token(), null, 200);

    assertThat(sheets.get("xl/workbook.xml"))
        .contains("name=\"Resumen\"", "name=\"Ventas\"", "name=\"Productos\"", "name=\"Cierres de caja\"", "name=\"Descuentos\"");
    String summary = sheets.get("xl/worksheets/sheet1.xml");
    assertThat(summary).contains("<v>7290.00</v>", "<v>810.00</v>"); // ventas y descuentos

    String sales = sheets.get("xl/worksheets/sheet2.xml");
    assertThat(sales).contains("#" + id(order).substring(0, 8).toUpperCase(), "<v>4000.00</v>", "<v>3290.00</v>");
    assertThat(sales).contains("SUM(I2:I2)"); // fila de totales con fórmula
    assertThat(countRows(sales)).isEqualTo(3); // encabezado + 1 pedido + totales

    String products = sheets.get("xl/worksheets/sheet3.xml");
    assertThat(products).contains("Latte", "Medialuna", "<v>6000.00</v>", "<v>2100.00</v>");

    String closings = sheets.get("xl/worksheets/sheet4.xml");
    assertThat(closings).contains("<v>5000.00</v>", "<v>9000.00</v>"); // inicial, esperado/contado

    String discounts = sheets.get("xl/worksheets/sheet5.xml");
    assertThat(discounts).contains("Cliente frecuente &amp; amigo &lt;3", "Todo el pedido", "<v>810.00</v>");
  }

  @Test
  @DisplayName("Solo dueño, administrador y supervisor lo descargan; fechas al revés o inválidas se rechazan")
  void permissionsAndValidation() throws Exception {
    Tenant cafe = newBusiness();
    Staff supervisor = newStaff(cafe, "SUPERVISOR");
    Staff mozo = newStaff(cafe, "WAITER");
    Staff cocina = newStaff(cafe, "KITCHEN");

    download(supervisor.token(), null, 200);
    download(mozo.token(), null, 403);
    download(cocina.token(), null, 403);

    Instant now = Instant.now();
    raw(cafe.token(), "/reports/excel?from=" + now + "&to=" + now.minus(1, ChronoUnit.DAYS), 400);
    raw(cafe.token(), "/reports/excel?from=ayer&to=hoy", 400);
    raw(cafe.token(), "/reports/excel?from=" + now.minus(1, ChronoUnit.DAYS) + "&to=" + now + "&tz=Marte/Olympus", 400);
  }

  @Test
  @DisplayName("Otro café no aparece en el reporte, ni se puede pedir el de un local ajeno")
  void tenantIsolation() throws Exception {
    Tenant cafe = newBusiness();
    Tenant otro = newBusiness();
    Staff cocinaOtro = newStaff(otro, "KITCHEN");
    String suyo = newProduct(otro, "Producto del otro café", "999");
    openCash(otro.token(), otro.locationId(), "0");
    JsonNode theirs = createOrder(otro.token(), otro.locationId(), null, item(suyo, 1));
    pay(otro.token(), id(theirs), "CASH", "999", 201);
    deliver(otro, cocinaOtro, id(theirs));

    Map<String, String> mine = download(cafe.token(), null, 200);
    assertThat(String.join("", mine.values())).doesNotContain("Producto del otro café");
    download(cafe.token(), otro.locationId(), 404);
  }

  // ---------------------------------------------------------------------

  private void deliver(Tenant cafe, Staff cocina, String orderId) throws Exception {
    setStatus(cocina.token(), orderId, "IN_PREPARATION", 200);
    setStatus(cocina.token(), orderId, "READY", 200);
    setStatus(cafe.token(), orderId, "DELIVERED", 200);
  }

  /** Baja el Excel del último día y devuelve cada archivo interno (ruta → contenido). */
  private Map<String, String> download(String token, String locationId, int expectedStatus) throws Exception {
    Instant to = Instant.now().plus(1, ChronoUnit.MINUTES);
    Instant from = to.minus(1, ChronoUnit.DAYS);
    String url = "/reports/excel?from=" + from + "&to=" + to + (locationId != null ? "&locationId=" + locationId : "");
    MvcResult result = raw(token, url, expectedStatus);
    Map<String, String> files = new HashMap<>();
    if (expectedStatus != 200) return files;
    assertThat(result.getResponse().getContentType()).contains("spreadsheetml");
    assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment", ".xlsx");
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
      }
    }
    return files;
  }

  private MvcResult raw(String token, String url, int expectedStatus) throws Exception {
    MvcResult result =
        mvc.perform(MockMvcRequestBuilders.get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn();
    assertThat(result.getResponse().getStatus())
        .as("GET %s respondió: %s", url, result.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .isEqualTo(expectedStatus);
    return result;
  }

  private static int countRows(String sheetXml) {
    return sheetXml.split("<row ", -1).length - 1;
  }
}
