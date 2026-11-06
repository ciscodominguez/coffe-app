package com.cofeapp.backend.legal;

import com.cofeapp.backend.common.context.TenantContext;
import com.cofeapp.backend.legal.dto.AcceptTermsRequest;
import com.cofeapp.backend.legal.dto.CreateServiceRequest;
import com.cofeapp.backend.legal.dto.CreatedServiceRequestResponse;
import com.cofeapp.backend.legal.dto.LegalStatusResponse;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los papeles (V17): términos y privacidad, y los botones de
 * arrepentimiento y de baja.
 *
 * <ul>
 *   <li>{@code GET /legal/version} y {@code POST /legal/requests}: públicos
 *       (ver SecurityConfig) — el formulario se usa sin tener que entrar.
 *   <li>{@code GET /legal/status}: cualquiera del café (el aviso para
 *       aceptar de nuevo solo se le muestra al dueño).
 *   <li>{@code POST /legal/accept}: solo el dueño (es quien contrata).
 * </ul>
 */
@RestController
@RequestMapping("/legal")
@RequiredArgsConstructor
public class LegalController {

  private final LegalService legalService;
  private final ServiceRequestService serviceRequestService;
  private final TenantContext tenantContext;

  @GetMapping("/version")
  public Map<String, String> version() {
    return Map.of("version", legalService.currentVersion());
  }

  @GetMapping("/status")
  public LegalStatusResponse status() {
    return legalService.status(tenantContext.getBusinessId());
  }

  @PostMapping("/accept")
  @PreAuthorize("hasAuthority('OWNER')")
  public LegalStatusResponse accept(@Valid @RequestBody AcceptTermsRequest request) {
    return legalService.accept(tenantContext.getBusinessId(), tenantContext.getUserId(), request.version());
  }

  @PostMapping("/requests")
  @ResponseStatus(HttpStatus.CREATED)
  public CreatedServiceRequestResponse createRequest(@Valid @RequestBody CreateServiceRequest request) {
    return serviceRequestService.create(request);
  }
}
