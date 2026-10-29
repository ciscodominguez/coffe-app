package com.cofeapp.backend.location;

import com.cofeapp.backend.common.exception.BadRequestException;
import com.cofeapp.backend.common.exception.NotFoundException;
import com.cofeapp.backend.domain.Location;
import com.cofeapp.backend.location.dto.LocationResponse;
import com.cofeapp.backend.location.dto.UpdateLocationRequest;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sin {@code create()} a propósito: todo negocio ya nace con un local por
 * defecto (ver {@code AuthService.registerBusiness}). Dar de alta un local
 * ADICIONAL es la funcionalidad de multi-sucursal que el roadmap ubica en
 * la Etapa 5 — este módulo, por ahora, solo lista y edita los locales
 * existentes del negocio.
 */
@Service
@RequiredArgsConstructor
public class LocationService {

  /** Anchos estándar de rollo térmico — mismo conjunto que el CHECK de la V7. */
  private static final Set<Integer> SUPPORTED_PAPER_WIDTHS_MM = Set.of(58, 80);

  private final LocationRepository locationRepository;

  public List<LocationResponse> findAll() {
    return locationRepository.findAllByOrderByCreatedAtAsc().stream().map(LocationResponse::from).toList();
  }

  public LocationResponse findOne(String id) {
    return LocationResponse.from(findOneOrThrow(id));
  }

  @Transactional
  public LocationResponse update(String id, UpdateLocationRequest request) {
    Location location = findOneOrThrow(id);

    if (request.getName() != null) location.setName(request.getName());
    if (request.getAddress() != null) location.setAddress(request.getAddress());
    if (request.getPhone() != null) location.setPhone(request.getPhone());
    if (request.getReceiptPaperWidthMm() != null) {
      if (!SUPPORTED_PAPER_WIDTHS_MM.contains(request.getReceiptPaperWidthMm())) {
        throw new BadRequestException("El ancho de papel tiene que ser 58 u 80 mm.");
      }
      location.setReceiptPaperWidthMm(request.getReceiptPaperWidthMm());
    }

    return LocationResponse.from(locationRepository.save(location));
  }

  private Location findOneOrThrow(String id) {
    // findByIdScoped (JPQL), no findById: ver TenantScopedEntity/
    // TenantSafeRepositoryImpl — es lo que hace que pedir el id de un
    // local de OTRO negocio dé "no encontrado".
    return locationRepository
        .findByIdScoped(id)
        .orElseThrow(() -> new NotFoundException("No se encontró el local " + id + "."));
  }
}
