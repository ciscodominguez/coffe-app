package com.cofeapp.backend.billing;

import com.cofeapp.backend.billing.dto.BillingStatusResponse;
import com.cofeapp.backend.billing.dto.CheckoutRequest;
import com.cofeapp.backend.billing.dto.CheckoutResponse;
import com.cofeapp.backend.common.context.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pantalla "Suscripción" del café (V16). Ver: dueño y administrador.
 * Suscribirse y cancelar: solo el dueño (es su tarjeta).
 *
 * <p>Es lo único (junto con /auth) que puede usar el dueño de un café
 * suspendido por falta de pago — ver JwtAuthenticationFilter.
 */
@RestController
@RequestMapping("/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

  private final BillingService billingService;
  private final TenantContext tenantContext;

  @GetMapping
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public BillingStatusResponse status() {
    return billingService.status(tenantContext.getBusinessId());
  }

  /** Al volver de Mercado Pago: pregunta cómo quedó la suscripción sin esperar el aviso. */
  @PostMapping("/refresh")
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public BillingStatusResponse refresh() {
    return billingService.refresh(tenantContext.getBusinessId());
  }

  @PostMapping("/checkout")
  @PreAuthorize("hasAuthority('OWNER')")
  public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest request) {
    return billingService.checkout(tenantContext.getBusinessId(), request.payerEmail());
  }

  @PostMapping("/cancel")
  @PreAuthorize("hasAuthority('OWNER')")
  public BillingStatusResponse cancel() {
    return billingService.cancel(tenantContext.getBusinessId());
  }
}
