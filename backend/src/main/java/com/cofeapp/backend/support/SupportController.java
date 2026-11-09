package com.cofeapp.backend.support;

import com.cofeapp.backend.support.dto.CreateSupportRequest;
import com.cofeapp.backend.support.dto.SupportInfoResponse;
import com.cofeapp.backend.support.dto.SupportRequestResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Botón de soporte (V18). {@code GET /support/info} es público (lo usa
 * también el login). {@code POST /support/requests}: cualquiera del café,
 * de cualquier rol (incluido el dueño de un café suspendido por falta de
 * pago — ver JwtAuthenticationFilter).
 */
@RestController
@RequestMapping("/support")
@RequiredArgsConstructor
public class SupportController {

  private final SupportService supportService;

  @GetMapping("/info")
  public SupportInfoResponse info() {
    return supportService.info();
  }

  @PostMapping("/requests")
  @ResponseStatus(HttpStatus.CREATED)
  public SupportRequestResponse create(@Valid @RequestBody CreateSupportRequest request) {
    return supportService.create(request);
  }
}
