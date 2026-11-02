package com.cofeapp.backend.mercadopago;

import com.cofeapp.backend.mercadopago.dto.ConfigureLocationQrRequest;
import com.cofeapp.backend.mercadopago.dto.ConnectMercadoPagoRequest;
import com.cofeapp.backend.mercadopago.dto.MercadoPagoStatusResponse;
import com.cofeapp.backend.mercadopago.dto.QrAvailabilityResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Cobros con QR": conectar la cuenta de Mercado Pago y configurar el QR
 * de cada local — solo OWNER/ADMIN, porque maneja la cuenta donde entra la
 * plata. La consulta de disponibilidad la usa la ventana de cobro, así que
 * la ve cualquier rol que cobra.
 */
@RestController
@RequestMapping("/mercadopago")
@RequiredArgsConstructor
public class MercadoPagoController {

  private final MercadoPagoAccountService accountService;

  @GetMapping("/account")
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public MercadoPagoStatusResponse status() {
    return accountService.status();
  }

  @PostMapping("/account")
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public MercadoPagoStatusResponse connect(@Valid @RequestBody ConnectMercadoPagoRequest request) {
    return accountService.connect(request.getAccessToken());
  }

  @DeleteMapping("/account")
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public MercadoPagoStatusResponse disconnect() {
    return accountService.disconnect();
  }

  @PatchMapping("/locations/{locationId}")
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public MercadoPagoStatusResponse configureLocation(
      @PathVariable String locationId, @Valid @RequestBody ConfigureLocationQrRequest request) {
    return accountService.configureLocation(locationId, request);
  }

  @GetMapping("/availability")
  @PreAuthorize("hasAnyAuthority('WAITER', 'SUPERVISOR', 'ADMIN', 'OWNER')")
  public QrAvailabilityResponse availability(@RequestParam String locationId) {
    return accountService.availability(locationId);
  }
}
