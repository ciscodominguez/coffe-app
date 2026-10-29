package com.cofeapp.backend.business;

import com.cofeapp.backend.business.dto.BusinessDisplayNameResponse;
import com.cofeapp.backend.business.dto.BusinessResponse;
import com.cofeapp.backend.business.dto.UpdateBusinessRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los datos completos del negocio (CUIT, condición fiscal, plan, estado)
 * son de gestión: {@code GET /businesses/me} queda restringido en el
 * BACKEND a OWNER/ADMIN/SUPERVISOR — mismo grupo que ya lo veía en el
 * frontend (roleGuard de {@code /business}), ahora reforzado en el
 * servidor para que no se pueda leer pegándole directo a la API.
 *
 * <p>{@code GET /businesses/me/display-name} es la excepción abierta a
 * cualquier rol autenticado: devuelve SOLO el nombre a mostrar (nombre de
 * fantasía, o razón social si no tiene), que es lo único que necesita el
 * reporte imprimible de cierre de Caja — y la Caja la abren/cierran también
 * los WAITER (ver README, sección 10).
 */
@RestController
@RequestMapping("/businesses")
@RequiredArgsConstructor
public class BusinessController {

  private final BusinessService businessService;

  @GetMapping("/me")
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN', 'SUPERVISOR')")
  public BusinessResponse findMe() {
    return businessService.findMe();
  }

  @GetMapping("/me/display-name")
  public BusinessDisplayNameResponse findMyDisplayName() {
    return businessService.findMyDisplayName();
  }

  @PatchMapping("/me")
  @PreAuthorize("hasAuthority('OWNER')")
  public BusinessResponse updateMe(@Valid @RequestBody UpdateBusinessRequest request) {
    return businessService.updateMe(request);
  }
}
