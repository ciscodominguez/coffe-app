package com.cofeapp.backend.floor;

import com.cofeapp.backend.floor.dto.FloorPlanResponse;
import com.cofeapp.backend.floor.dto.SaveAreaRequest;
import com.cofeapp.backend.floor.dto.SaveLayoutRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plano del salón (V18). Verlo: cualquiera del café (el mozo lo usa para
 * elegir la mesa). Armarlo: dueño y administrador (mismo criterio que
 * crear o editar mesas en {@code TableController}).
 */
@RestController
@RequestMapping("/floor")
@RequiredArgsConstructor
public class FloorController {

  private final FloorService floorService;

  @GetMapping
  public FloorPlanResponse plan(@RequestParam String locationId) {
    return floorService.plan(locationId);
  }

  @PostMapping("/areas")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public FloorPlanResponse.Area createArea(@Valid @RequestBody SaveAreaRequest request) {
    return floorService.createArea(request);
  }

  @PatchMapping("/areas/{id}")
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public FloorPlanResponse.Area updateArea(@PathVariable String id, @Valid @RequestBody SaveAreaRequest request) {
    return floorService.updateArea(id, request);
  }

  @DeleteMapping("/areas/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public void deleteArea(@PathVariable String id) {
    floorService.deleteArea(id);
  }

  @PutMapping("/areas/{id}/layout")
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public FloorPlanResponse saveLayout(@PathVariable String id, @Valid @RequestBody SaveLayoutRequest request) {
    return floorService.saveLayout(id, request);
  }
}
