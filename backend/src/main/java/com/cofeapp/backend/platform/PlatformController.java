package com.cofeapp.backend.platform;

import com.cofeapp.backend.billing.BillingService;
import com.cofeapp.backend.billing.dto.PlatformSettingsDto;
import com.cofeapp.backend.common.security.AuthenticatedUser;
import com.cofeapp.backend.legal.ServiceRequestService;
import com.cofeapp.backend.support.SupportService;
import com.cofeapp.backend.support.dto.SupportRequestResponse;
import com.cofeapp.backend.support.dto.UpdateSupportRequest;
import com.cofeapp.backend.legal.dto.ServiceRequestResponse;
import com.cofeapp.backend.legal.dto.UpdateServiceRequest;
import com.cofeapp.backend.domain.PlatformAdmin;
import com.cofeapp.backend.platform.dto.BusinessDetailResponse;
import com.cofeapp.backend.platform.dto.BusinessSummaryResponse;
import com.cofeapp.backend.platform.dto.UpdateBusinessStatusRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panel de superadmin (V15). Toda la ruta {@code /platform/**} exige el
 * token de plataforma (ver {@code SecurityConfig}); ningún usuario de un
 * café puede entrar, ni siquiera un dueño.
 */
@RestController
@RequestMapping("/platform")
@RequiredArgsConstructor
public class PlatformController {

  private final PlatformService platformService;
  private final BillingService billingService;
  private final ServiceRequestService serviceRequestService;
  private final SupportService supportService;

  @GetMapping("/me")
  public Map<String, String> me(@AuthenticationPrincipal AuthenticatedUser user) {
    PlatformAdmin admin = platformService.me(user.userId());
    return Map.of("email", admin.getEmail(), "name", (admin.getFirstName() + " " + admin.getLastName()).trim());
  }

  /** V16 — precio de la suscripción, días de prueba y de gracia. */
  @GetMapping("/settings")
  public PlatformSettingsDto settings() {
    return billingService.settingsDto();
  }

  @PutMapping("/settings")
  public PlatformSettingsDto updateSettings(@Valid @RequestBody PlatformSettingsDto request) {
    return billingService.updateSettings(request);
  }

  /** V17 — pedidos del "Botón de arrepentimiento" y del "Botón de baja". */
  @GetMapping("/requests")
  public List<ServiceRequestResponse> requests() {
    return serviceRequestService.findAll();
  }

  @PatchMapping("/requests/{id}")
  public ServiceRequestResponse updateRequest(@PathVariable String id, @Valid @RequestBody UpdateServiceRequest request) {
    return serviceRequestService.update(id, request);
  }

  /** V18 — pedidos de ayuda del botón de soporte. */
  @GetMapping("/support")
  public List<SupportRequestResponse> supportRequests() {
    return supportService.findAll();
  }

  @PatchMapping("/support/{id}")
  public SupportRequestResponse updateSupportRequest(@PathVariable String id, @Valid @RequestBody UpdateSupportRequest request) {
    return supportService.update(id, request);
  }

  @GetMapping("/businesses")
  public List<BusinessSummaryResponse> businesses() {
    return platformService.findAll();
  }

  @GetMapping("/businesses/{id}")
  public BusinessDetailResponse business(@PathVariable String id) {
    return platformService.findOne(id);
  }

  @PatchMapping("/businesses/{id}")
  public BusinessDetailResponse update(
      @PathVariable String id,
      @Valid @RequestBody UpdateBusinessStatusRequest request,
      @AuthenticationPrincipal AuthenticatedUser user) {
    return platformService.update(id, request, user.userId());
  }
}
