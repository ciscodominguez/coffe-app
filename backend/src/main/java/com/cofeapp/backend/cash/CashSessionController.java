package com.cofeapp.backend.cash;

import com.cofeapp.backend.cash.dto.CashMovementResponse;
import com.cofeapp.backend.cash.dto.CashSessionResponse;
import com.cofeapp.backend.cash.dto.CloseCashSessionRequest;
import com.cofeapp.backend.cash.dto.CreateCashMovementRequest;
import com.cofeapp.backend.cash.dto.OpenCashSessionRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ítem 14 de las mejoras pedidas ("gestión de la caja"). Mismo criterio de
 * roles en TODOS los endpoints, lectura incluida (a diferencia de
 * Category/Product, acá no se abre a cualquier rol autenticado): {@code
 * hasAnyAuthority('WAITER', 'SUPERVISOR', 'ADMIN', 'OWNER')} — "todos los
 * que atienden" pueden abrir/cerrar su turno, cargar movimientos y ver el
 * estado de la caja; KITCHEN nunca (no maneja dinero), mismo grupo que
 * {@code FRONT_OF_HOUSE_ROLES} en {@code OrderService}.
 */
@RestController
@RequestMapping("/cash-sessions")
@RequiredArgsConstructor
public class CashSessionController {

  private static final String FRONT_OF_HOUSE = "hasAnyAuthority('WAITER', 'SUPERVISOR', 'ADMIN', 'OWNER')";

  private final CashSessionService cashSessionService;

  /** {@code ?locationId=...} opcional — sin él, devuelve el historial de todo el negocio. */
  @GetMapping
  @PreAuthorize(FRONT_OF_HOUSE)
  public List<CashSessionResponse> findAll(@RequestParam(required = false) String locationId) {
    return locationId != null ? cashSessionService.findAllByLocation(locationId) : cashSessionService.findAll();
  }

  /** {@code ?locationId=...} obligatorio — el turno abierto de ESE local, si hay uno. */
  @GetMapping("/current")
  @PreAuthorize(FRONT_OF_HOUSE)
  public CashSessionResponse current(@RequestParam String locationId) {
    return cashSessionService.findCurrent(locationId);
  }

  @GetMapping("/{id}")
  @PreAuthorize(FRONT_OF_HOUSE)
  public CashSessionResponse findOne(@PathVariable String id) {
    return cashSessionService.findOne(id);
  }

  @PostMapping("/open")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(FRONT_OF_HOUSE)
  public CashSessionResponse open(@Valid @RequestBody OpenCashSessionRequest request) {
    return cashSessionService.open(request);
  }

  @PostMapping("/{id}/movements")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(FRONT_OF_HOUSE)
  public CashMovementResponse addMovement(
      @PathVariable String id, @Valid @RequestBody CreateCashMovementRequest request) {
    return cashSessionService.addMovement(id, request);
  }

  @PostMapping("/{id}/close")
  @PreAuthorize(FRONT_OF_HOUSE)
  public CashSessionResponse close(@PathVariable String id, @Valid @RequestBody CloseCashSessionRequest request) {
    return cashSessionService.close(id, request);
  }
}
