package com.cofeapp.backend.location;

import com.cofeapp.backend.location.dto.LocationResponse;
import com.cofeapp.backend.location.dto.UpdateLocationRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La LECTURA de locales queda abierta a cualquier rol autenticado a
 * propósito (es la excepción al blindaje de gestión del backend): Meseras,
 * KDS, Caja e Insumos resuelven el local actual con {@code GET /locations}
 * cuando el JWT no trae {@code locationId} (ver {@code resolveLocationId}
 * en esos componentes), y un local solo expone nombre/dirección/teléfono —
 * nada sensible. La EDICIÓN sí es solo OWNER/ADMIN.
 */
@RestController
@RequestMapping("/locations")
@RequiredArgsConstructor
public class LocationController {

  private final LocationService locationService;

  @GetMapping
  public List<LocationResponse> findAll() {
    return locationService.findAll();
  }

  @GetMapping("/{id}")
  public LocationResponse findOne(@PathVariable String id) {
    return locationService.findOne(id);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public LocationResponse update(@PathVariable String id, @Valid @RequestBody UpdateLocationRequest request) {
    return locationService.update(id, request);
  }
}
