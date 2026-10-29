package com.cofeapp.backend.table;

import com.cofeapp.backend.common.exception.ConflictException;
import com.cofeapp.backend.common.exception.NotFoundException;
import com.cofeapp.backend.domain.RestaurantTable;
import com.cofeapp.backend.location.LocationRepository;
import com.cofeapp.backend.table.dto.CreateTableRequest;
import com.cofeapp.backend.table.dto.TableResponse;
import com.cofeapp.backend.table.dto.UpdateTableRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TableService {

  private final TableRepository tableRepository;
  private final LocationRepository locationRepository;

  public List<TableResponse> findAll() {
    return tableRepository.findAllByOrderByNumberAsc().stream().map(TableResponse::from).toList();
  }

  public List<TableResponse> findAllByLocation(String locationId) {
    assertLocationExists(locationId);
    return tableRepository.findAllByLocationIdOrderByNumberAsc(locationId).stream()
        .map(TableResponse::from)
        .toList();
  }

  public TableResponse findOne(String id) {
    return TableResponse.from(findOneOrThrow(id));
  }

  @Transactional
  public TableResponse create(CreateTableRequest request) {
    assertLocationExists(request.getLocationId());
    assertNumberFree(request.getLocationId(), request.getNumber(), null);

    RestaurantTable table = new RestaurantTable();
    table.setLocationId(request.getLocationId());
    table.setNumber(request.getNumber());
    table.setLabel(request.getLabel());
    table.setCapacity(request.getCapacity());

    return TableResponse.from(tableRepository.save(table));
  }

  @Transactional
  public TableResponse update(String id, UpdateTableRequest request) {
    RestaurantTable table = findOneOrThrow(id);

    if (request.getNumber() != null && !request.getNumber().equals(table.getNumber())) {
      assertNumberFree(table.getLocationId(), request.getNumber(), id);
      table.setNumber(request.getNumber());
    }
    if (request.getLabel() != null) table.setLabel(request.getLabel());
    if (request.getCapacity() != null) table.setCapacity(request.getCapacity());
    if (request.getStatus() != null) table.setStatus(request.getStatus());
    if (request.getIsActive() != null) table.setIsActive(request.getIsActive());

    return TableResponse.from(tableRepository.save(table));
  }

  /** Baja lógica ({@code isActive = false}), nunca un DELETE físico — mismo criterio que el resto del catálogo. */
  @Transactional
  public void deactivate(String id) {
    RestaurantTable table = findOneOrThrow(id);
    table.setIsActive(false);
    tableRepository.save(table);
  }

  /** Espejo del UNIQUE KEY {@code uq_tables_location_number} de la migración. */
  private void assertNumberFree(String locationId, Integer number, String excludingId) {
    boolean taken =
        excludingId == null
            ? tableRepository.existsByLocationIdAndNumber(locationId, number)
            : tableRepository.existsByLocationIdAndNumberAndIdNot(locationId, number, excludingId);
    if (taken) {
      throw new ConflictException("Ya existe la mesa número " + number + " en ese local.");
    }
  }

  private void assertLocationExists(String locationId) {
    // findByIdScoped, no findById: ver TenantScopedEntity/TenantSafeRepositoryImpl.
    locationRepository
        .findByIdScoped(locationId)
        .orElseThrow(() -> new NotFoundException("No se encontró el local " + locationId + "."));
  }

  private RestaurantTable findOneOrThrow(String id) {
    return tableRepository
        .findByIdScoped(id)
        .orElseThrow(() -> new NotFoundException("No se encontró la mesa " + id + "."));
  }
}
