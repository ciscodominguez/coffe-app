package com.cofeapp.backend.floor;

import com.cofeapp.backend.common.exception.BadRequestException;
import com.cofeapp.backend.common.exception.ConflictException;
import com.cofeapp.backend.common.exception.NotFoundException;
import com.cofeapp.backend.domain.FloorArea;
import com.cofeapp.backend.domain.FloorItem;
import com.cofeapp.backend.domain.RestaurantTable;
import com.cofeapp.backend.floor.dto.FloorPlanResponse;
import com.cofeapp.backend.floor.dto.SaveAreaRequest;
import com.cofeapp.backend.floor.dto.SaveLayoutRequest;
import com.cofeapp.backend.location.LocationRepository;
import com.cofeapp.backend.table.TableRepository;
import com.cofeapp.backend.table.dto.TableResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plano del salón (V18). Cada zona es un lienzo de {@value #WIDTH} x
 * {@value #HEIGHT} unidades; las mesas y referencias tienen que quedar
 * adentro, con un tamaño entre {@value #MIN_SIZE} y {@value #MAX_SIZE}
 * unidades por lado.
 *
 * <p>Todo pasa por los repositorios con el filtro del café activo: una
 * zona, mesa o local de otro café da 404.
 */
@Service
@RequiredArgsConstructor
public class FloorService {

  static final int WIDTH = 100;
  static final int HEIGHT = 75;
  static final int MIN_SIZE = 2;
  static final int MAX_SIZE = 100;

  private final FloorAreaRepository areaRepository;
  private final FloorItemRepository itemRepository;
  private final TableRepository tableRepository;
  private final LocationRepository locationRepository;

  @Transactional(readOnly = true)
  public FloorPlanResponse plan(String locationId) {
    assertLocation(locationId);
    List<FloorArea> areas = areaRepository.findAllByLocationIdOrderByPositionAscCreatedAtAsc(locationId);
    List<String> areaIds = areas.stream().map(FloorArea::getId).toList();
    List<FloorPlanResponse.Item> items =
        areaIds.isEmpty()
            ? List.of()
            : itemRepository.findAllByAreaIdIn(areaIds).stream()
                .map(i -> new FloorPlanResponse.Item(i.getId(), i.getAreaId(), i.getKind(), i.getLabel(), i.getX(), i.getY(), i.getWidth(), i.getHeight()))
                .toList();
    List<TableResponse> tables =
        tableRepository.findAllByLocationIdOrderByNumberAsc(locationId).stream()
            .filter(t -> Boolean.TRUE.equals(t.getIsActive()))
            .map(TableResponse::from)
            .toList();
    return new FloorPlanResponse(
        areas.stream().map(a -> new FloorPlanResponse.Area(a.getId(), a.getName(), a.getPosition())).toList(), items, tables);
  }

  @Transactional
  public FloorPlanResponse.Area createArea(SaveAreaRequest request) {
    if (request.locationId() == null || request.locationId().isBlank()) throw new BadRequestException("Falta el local.");
    assertLocation(request.locationId());
    String name = request.name().trim();
    if (areaRepository.existsByLocationIdAndNameIgnoreCase(request.locationId(), name)) {
      throw new ConflictException("Ya hay una zona \"" + name + "\" en este local.");
    }
    FloorArea area = new FloorArea();
    area.setLocationId(request.locationId());
    area.setName(name);
    area.setPosition(
        request.position() != null
            ? request.position()
            : areaRepository.findAllByLocationIdOrderByPositionAscCreatedAtAsc(request.locationId()).size());
    area = areaRepository.save(area);
    return new FloorPlanResponse.Area(area.getId(), area.getName(), area.getPosition());
  }

  @Transactional
  public FloorPlanResponse.Area updateArea(String id, SaveAreaRequest request) {
    FloorArea area = findArea(id);
    String name = request.name().trim();
    if (areaRepository.existsByLocationIdAndNameIgnoreCaseAndIdNot(area.getLocationId(), name, id)) {
      throw new ConflictException("Ya hay una zona \"" + name + "\" en este local.");
    }
    area.setName(name);
    if (request.position() != null) area.setPosition(request.position());
    area = areaRepository.save(area);
    return new FloorPlanResponse.Area(area.getId(), area.getName(), area.getPosition());
  }

  /** Borra la zona y sus referencias; sus mesas no se borran: quedan sin ubicar. */
  @Transactional
  public void deleteArea(String id) {
    FloorArea area = findArea(id);
    for (RestaurantTable table : tableRepository.findAllByLocationIdOrderByNumberAsc(area.getLocationId())) {
      if (id.equals(table.getAreaId())) {
        unplace(table);
        tableRepository.save(table);
      }
    }
    itemRepository.deleteAll(itemRepository.findAllByAreaId(id));
    areaRepository.delete(area);
  }

  /** Guarda cómo quedó una zona (ver {@link SaveLayoutRequest}). */
  @Transactional
  public FloorPlanResponse saveLayout(String areaId, SaveLayoutRequest request) {
    FloorArea area = findArea(areaId);
    Map<String, RestaurantTable> tablesOfLocation = new HashMap<>();
    tableRepository.findAllByLocationIdOrderByNumberAsc(area.getLocationId()).forEach(t -> tablesOfLocation.put(t.getId(), t));

    Set<String> placed = new HashSet<>();
    for (SaveLayoutRequest.TablePlacement p : request.tables()) {
      RestaurantTable table = tablesOfLocation.get(p.id());
      if (table == null) throw new NotFoundException("No se encontró la mesa " + p.id() + " en este local.");
      if (!placed.add(p.id())) throw new BadRequestException("La mesa " + table.getNumber() + " aparece dos veces.");
      checkBox("La mesa " + table.getNumber(), p.x(), p.y(), p.width(), p.height());
      table.setAreaId(areaId);
      table.setX(round(p.x()));
      table.setY(round(p.y()));
      table.setWidth(round(p.width()));
      table.setHeight(round(p.height()));
      table.setShape(p.shape());
    }
    for (RestaurantTable table : tablesOfLocation.values()) {
      if (areaId.equals(table.getAreaId()) && !placed.contains(table.getId())) unplace(table);
    }
    tableRepository.saveAll(tablesOfLocation.values());

    Map<String, FloorItem> existing = new HashMap<>();
    itemRepository.findAllByAreaId(areaId).forEach(i -> existing.put(i.getId(), i));
    Set<String> kept = new HashSet<>();
    for (SaveLayoutRequest.ItemPlacement p : request.items()) {
      checkBox("Una referencia", p.x(), p.y(), p.width(), p.height());
      FloorItem item = p.id() != null ? existing.get(p.id()) : null;
      if (item == null) {
        item = new FloorItem();
        item.setAreaId(areaId);
      } else {
        kept.add(item.getId());
      }
      item.setKind(p.kind());
      item.setLabel(p.label() == null || p.label().isBlank() ? null : p.label().trim());
      item.setX(round(p.x()));
      item.setY(round(p.y()));
      item.setWidth(round(p.width()));
      item.setHeight(round(p.height()));
      itemRepository.save(item);
    }
    existing.values().stream().filter(i -> !kept.contains(i.getId())).forEach(itemRepository::delete);

    return plan(area.getLocationId());
  }

  // ---------------------------------------------------------------------

  private static void checkBox(String what, BigDecimal x, BigDecimal y, BigDecimal w, BigDecimal h) {
    double dx = x.doubleValue(), dy = y.doubleValue(), dw = w.doubleValue(), dh = h.doubleValue();
    if (dw < MIN_SIZE || dh < MIN_SIZE || dw > MAX_SIZE || dh > MAX_SIZE) {
      throw new BadRequestException(what + " tiene un tamaño inválido.");
    }
    if (dx < 0 || dy < 0 || dx + dw > WIDTH + 0.01 || dy + dh > HEIGHT + 0.01) {
      throw new BadRequestException(what + " quedó afuera del plano.");
    }
  }

  private static BigDecimal round(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  private static void unplace(RestaurantTable table) {
    table.setAreaId(null);
    table.setX(null);
    table.setY(null);
    table.setWidth(null);
    table.setHeight(null);
  }

  private FloorArea findArea(String id) {
    return areaRepository.findByIdScoped(id).orElseThrow(() -> new NotFoundException("No se encontró la zona."));
  }

  private void assertLocation(String locationId) {
    locationRepository.findByIdScoped(locationId).orElseThrow(() -> new NotFoundException("No se encontró el local."));
  }
}
