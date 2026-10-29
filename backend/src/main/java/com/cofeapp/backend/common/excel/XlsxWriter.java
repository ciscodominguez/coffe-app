package com.cofeapp.backend.common.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Arma un archivo Excel (.xlsx) sin librerías externas.
 *
 * <p>Un .xlsx es un .zip con unos pocos XML adentro (formato Office Open
 * XML). Para los reportes alcanza con texto, números, plata, fechas, una
 * fila de encabezado en negrita (fija al bajar y con filtros) y una fila de
 * totales con fórmulas {@code SUMA}. Hacerlo a mano evita sumar Apache POI
 * (varios MB de dependencias) para eso.
 *
 * <pre>
 *   XlsxWriter book = new XlsxWriter();
 *   XlsxWriter.Sheet sales = book.sheet("Ventas");
 *   sales.header("Fecha", "Total");
 *   sales.row(Cell.dateTime(when), Cell.money(total));
 *   sales.totals("Total", 1);   // SUMA de la columna B
 *   byte[] file = book.toBytes();
 * </pre>
 */
public class XlsxWriter {

  // Estilos (índices de cellXfs en styles.xml).
  private static final int STYLE_DEFAULT = 0;
  private static final int STYLE_HEADER = 1;
  private static final int STYLE_MONEY = 2;
  private static final int STYLE_DATE_TIME = 3;
  private static final int STYLE_MONEY_BOLD = 4;
  private static final int STYLE_BOLD = 5;
  private static final int STYLE_INTEGER = 6;
  private static final int STYLE_DATE = 7;
  private static final int STYLE_INTEGER_BOLD = 8;

  private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

  private final List<Sheet> sheets = new ArrayList<>();

  public Sheet sheet(String name) {
    Sheet sheet = new Sheet(safeSheetName(name));
    sheets.add(sheet);
    return sheet;
  }

  // ---------------------------------------------------------------------
  // Celdas
  // ---------------------------------------------------------------------

  /** Una celda: texto, número, plata o fecha. {@code null} = celda vacía. */
  public record Cell(Kind kind, String text, BigDecimal number, LocalDateTime dateTime, boolean bold) {

    enum Kind { TEXT, MONEY, INTEGER, DECIMAL, DATE_TIME, DATE, EMPTY }

    public static Cell text(String value) {
      return value == null ? empty() : new Cell(Kind.TEXT, value, null, null, false);
    }

    public static Cell bold(String value) {
      return new Cell(Kind.TEXT, value == null ? "" : value, null, null, true);
    }

    public static Cell money(BigDecimal value) {
      return value == null ? empty() : new Cell(Kind.MONEY, null, value, null, false);
    }

    public static Cell boldMoney(BigDecimal value) {
      return value == null ? empty() : new Cell(Kind.MONEY, null, value, null, true);
    }

    public static Cell integer(long value) {
      return new Cell(Kind.INTEGER, null, BigDecimal.valueOf(value), null, false);
    }

    /** Número con decimales, sin formato de plata (ej. un porcentaje "10"). */
    public static Cell decimal(BigDecimal value) {
      return value == null ? empty() : new Cell(Kind.DECIMAL, null, value, null, false);
    }

    public static Cell dateTime(LocalDateTime value) {
      return value == null ? empty() : new Cell(Kind.DATE_TIME, null, null, value, false);
    }

    public static Cell date(LocalDate value) {
      return value == null ? empty() : new Cell(Kind.DATE, null, null, value.atStartOfDay(), false);
    }

    public static Cell empty() {
      return new Cell(Kind.EMPTY, null, null, null, false);
    }
  }

  // ---------------------------------------------------------------------
  // Hojas
  // ---------------------------------------------------------------------

  public static final class Sheet {
    private final String name;
    private final List<String> rowsXml = new ArrayList<>();
    private int[] widths = new int[0];
    private int rowCount = 0;
    private int headerColumns = 0;
    private boolean hasHeader = false;
    private int firstDataRow = 0;
    /** Suma de cada columna numérica (para guardar el resultado junto a la fórmula de totales). */
    private final java.util.Map<Integer, BigDecimal> columnSums = new java.util.HashMap<>();

    private Sheet(String name) {
      this.name = name;
    }

    /** Ancho de cada columna, en caracteres aproximados. */
    public Sheet widths(int... widths) {
      this.widths = widths;
      return this;
    }

    /** Fila de encabezado: negrita, fondo gris, queda fija al bajar y con filtros. */
    public Sheet header(String... titles) {
      rowCount++;
      StringBuilder xml = new StringBuilder("<row r=\"").append(rowCount).append("\">");
      for (int i = 0; i < titles.length; i++) {
        xml.append(cellXml(i, rowCount, Cell.text(titles[i]), STYLE_HEADER));
      }
      rowsXml.add(xml.append("</row>").toString());
      hasHeader = true;
      headerColumns = titles.length;
      firstDataRow = rowCount + 1;
      return this;
    }

    public Sheet row(Cell... cells) {
      rowCount++;
      StringBuilder xml = new StringBuilder("<row r=\"").append(rowCount).append("\">");
      for (int i = 0; i < cells.length; i++) {
        xml.append(cellXml(i, rowCount, cells[i], -1));
        if (cells[i].number() != null) columnSums.merge(i, cells[i].number(), BigDecimal::add);
      }
      rowsXml.add(xml.append("</row>").toString());
      return this;
    }

    /** Fila en blanco (separador). */
    public Sheet blank() {
      rowCount++;
      rowsXml.add("<row r=\"" + rowCount + "\"/>");
      return this;
    }

    /**
     * Fila de totales: {@code label} en la primera columna y, en cada
     * columna indicada (0 = A), la {@code SUMA} de todas las filas de datos.
     * {@code integerColumns}: columnas que suman cantidades, no plata.
     */
    public Sheet totals(String label, int[] moneyColumns, int... integerColumns) {
      int lastDataRow = rowCount;
      rowCount++;
      StringBuilder xml = new StringBuilder("<row r=\"").append(rowCount).append("\">");
      xml.append(cellXml(0, rowCount, Cell.bold(label), -1));
      for (int column : moneyColumns) xml.append(sumCell(column, lastDataRow, STYLE_MONEY_BOLD));
      for (int column : integerColumns) xml.append(sumCell(column, lastDataRow, STYLE_INTEGER_BOLD));
      rowsXml.add(xml.append("</row>").toString());
      return this;
    }

    private String sumCell(int column, int lastDataRow, int style) {
      String letter = columnLetter(column);
      String ref = letter + rowCount;
      if (lastDataRow < firstDataRow) {
        return "<c r=\"" + ref + "\" s=\"" + style + "\"><v>0</v></c>";
      }
      String formula = "SUM(" + letter + firstDataRow + ":" + letter + lastDataRow + ")";
      // Fórmula (se actualiza si el contador edita) + el resultado ya calculado.
      BigDecimal sum = columnSums.getOrDefault(column, BigDecimal.ZERO);
      return "<c r=\"" + ref + "\" s=\"" + style + "\"><f>" + formula + "</f><v>" + sum.toPlainString() + "</v></c>";
    }

    private String toXml() {
      StringBuilder xml = new StringBuilder();
      xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
      xml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
      if (hasHeader) {
        xml.append("<sheetViews><sheetView workbookViewId=\"0\">")
            .append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
            .append("</sheetView></sheetViews>");
      }
      if (widths.length > 0) {
        xml.append("<cols>");
        for (int i = 0; i < widths.length; i++) {
          xml.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
              .append("\" width=\"").append(widths[i]).append("\" customWidth=\"1\"/>");
        }
        xml.append("</cols>");
      }
      xml.append("<sheetData>");
      rowsXml.forEach(xml::append);
      xml.append("</sheetData>");
      if (hasHeader && headerColumns > 0) {
        xml.append("<autoFilter ref=\"A1:").append(columnLetter(headerColumns - 1)).append(Math.max(1, firstDataRow - 1)).append("\"/>");
      }
      xml.append("</worksheet>");
      return xml.toString();
    }
  }

  // ---------------------------------------------------------------------
  // Salida
  // ---------------------------------------------------------------------

  public byte[] toBytes() {
    if (sheets.isEmpty()) sheet("Hoja1");
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes)) {
      put(zip, "[Content_Types].xml", contentTypes());
      put(zip, "_rels/.rels", rootRels());
      put(zip, "xl/workbook.xml", workbook());
      put(zip, "xl/_rels/workbook.xml.rels", workbookRels());
      put(zip, "xl/styles.xml", styles());
      for (int i = 0; i < sheets.size(); i++) {
        put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", sheets.get(i).toXml());
      }
      zip.finish();
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo armar el Excel.", e);
    }
  }

  private static void put(ZipOutputStream zip, String path, String content) throws IOException {
    zip.putNextEntry(new ZipEntry(path));
    zip.write(content.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  private String contentTypes() {
    StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        .append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        .append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
    for (int i = 0; i < sheets.size(); i++) {
      xml.append("<Override PartName=\"/xl/worksheets/sheet").append(i + 1)
          .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
    }
    return xml.append("</Types>").toString();
  }

  private static String rootRels() {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
        + "</Relationships>";
  }

  private String workbook() {
    StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        .append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
        .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
        .append("<sheets>");
    for (int i = 0; i < sheets.size(); i++) {
      xml.append("<sheet name=\"").append(escape(sheets.get(i).name)).append("\" sheetId=\"").append(i + 1)
          .append("\" r:id=\"rId").append(i + 1).append("\"/>");
    }
    xml.append("</sheets>");
    // Rango del filtro de cada hoja con encabezado (Excel lo pide como nombre oculto).
    StringBuilder names = new StringBuilder();
    for (int i = 0; i < sheets.size(); i++) {
      Sheet sheet = sheets.get(i);
      if (sheet.hasHeader && sheet.headerColumns > 0) {
        names.append("<definedName name=\"_xlnm._FilterDatabase\" localSheetId=\"").append(i).append("\" hidden=\"1\">'")
            .append(escape(sheet.name.replace("'", "''"))).append("'!$A$1:$")
            .append(columnLetter(sheet.headerColumns - 1)).append("$").append(Math.max(1, sheet.firstDataRow - 1))
            .append("</definedName>");
      }
    }
    if (names.length() > 0) xml.append("<definedNames>").append(names).append("</definedNames>");
    xml.append("<calcPr calcId=\"0\" fullCalcOnLoad=\"1\"/>");
    return xml.append("</workbook>").toString();
  }

  private String workbookRels() {
    StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        .append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
    for (int i = 0; i < sheets.size(); i++) {
      xml.append("<Relationship Id=\"rId").append(i + 1)
          .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
          .append(i + 1).append(".xml\"/>");
    }
    xml.append("<Relationship Id=\"rId").append(sheets.size() + 1)
        .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
    return xml.append("</Relationships>").toString();
  }

  private static String styles() {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
        + "<numFmts count=\"3\">"
        + "<numFmt numFmtId=\"164\" formatCode=\"#,##0.00\"/>"
        + "<numFmt numFmtId=\"165\" formatCode=\"dd/mm/yyyy hh:mm\"/>"
        + "<numFmt numFmtId=\"166\" formatCode=\"dd/mm/yyyy\"/>"
        + "</numFmts>"
        + "<fonts count=\"2\">"
        + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
        + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
        + "</fonts>"
        + "<fills count=\"3\">"
        + "<fill><patternFill patternType=\"none\"/></fill>"
        + "<fill><patternFill patternType=\"gray125\"/></fill>"
        + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE7E2DC\"/><bgColor indexed=\"64\"/></patternFill></fill>"
        + "</fills>"
        + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
        + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
        + "<cellXfs count=\"9\">"
        + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
        + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
        + "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
        + "<xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
        + "<xf numFmtId=\"164\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\"/>"
        + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
        + "<xf numFmtId=\"1\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
        + "<xf numFmtId=\"166\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
        + "<xf numFmtId=\"1\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\"/>"
        + "</cellXfs>"
        + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
        + "</styleSheet>";
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  private static String cellXml(int column, int row, Cell cell, int forcedStyle) {
    String ref = columnLetter(column) + row;
    return switch (cell.kind()) {
      case EMPTY -> "";
      case TEXT -> {
        int style = forcedStyle >= 0 ? forcedStyle : cell.bold() ? STYLE_BOLD : STYLE_DEFAULT;
        yield "<c r=\"" + ref + "\" t=\"inlineStr\"" + (style != 0 ? " s=\"" + style + "\"" : "")
            + "><is><t xml:space=\"preserve\">" + escape(cell.text()) + "</t></is></c>";
      }
      case MONEY -> numberCell(ref, cell.number(), cell.bold() ? STYLE_MONEY_BOLD : STYLE_MONEY);
      case INTEGER -> numberCell(ref, cell.number(), STYLE_INTEGER);
      case DECIMAL -> numberCell(ref, cell.number(), STYLE_DEFAULT);
      case DATE_TIME -> numberCell(ref, excelSerial(cell.dateTime()), STYLE_DATE_TIME);
      case DATE -> numberCell(ref, excelSerial(cell.dateTime()), STYLE_DATE);
    };
  }

  private static String numberCell(String ref, BigDecimal value, int style) {
    return "<c r=\"" + ref + "\"" + (style != 0 ? " s=\"" + style + "\"" : "") + "><v>" + value.toPlainString() + "</v></c>";
  }

  /** Excel guarda las fechas como días desde el 30/12/1899 (con la hora como fracción). */
  private static BigDecimal excelSerial(LocalDateTime value) {
    long days = ChronoUnit.DAYS.between(EXCEL_EPOCH, value.toLocalDate());
    long seconds = value.toLocalTime().toSecondOfDay();
    return BigDecimal.valueOf(days).add(BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(86400), 10, java.math.RoundingMode.HALF_UP));
  }

  /** 0 → A, 25 → Z, 26 → AA. */
  static String columnLetter(int index) {
    StringBuilder letters = new StringBuilder();
    int n = index + 1;
    while (n > 0) {
      int rem = (n - 1) % 26;
      letters.insert(0, (char) ('A' + rem));
      n = (n - 1) / 26;
    }
    return letters.toString();
  }

  /** Escapa para XML y saca caracteres de control que Excel no acepta. */
  static String escape(String value) {
    StringBuilder out = new StringBuilder(value.length());
    for (char c : value.toCharArray()) {
      switch (c) {
        case '&' -> out.append("&amp;");
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        case '"' -> out.append("&quot;");
        default -> {
          if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') out.append(c);
        }
      }
    }
    return out.toString();
  }

  /** Excel: hasta 31 caracteres y sin {@code \ / ? * [ ] :}. */
  private static String safeSheetName(String name) {
    String cleaned = name.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
    if (cleaned.isEmpty()) cleaned = "Hoja";
    return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
  }
}
