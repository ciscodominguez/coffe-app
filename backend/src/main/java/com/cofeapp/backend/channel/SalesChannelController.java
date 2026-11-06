package com.cofeapp.backend.channel;

import com.cofeapp.backend.channel.dto.SalesChannelResponse;
import com.cofeapp.backend.channel.dto.SaveSalesChannelRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Apps de delivery de cada local (V19). Verlas: cualquiera del café (quien
 * toma pedidos necesita saber cuáles están prendidas). Configurarlas: dueño
 * y administrador.
 */
@RestController
@RequestMapping("/sales-channels")
@RequiredArgsConstructor
public class SalesChannelController {

  private final SalesChannelService service;

  @GetMapping
  public List<SalesChannelResponse> findAll(@RequestParam(required = false) String locationId) {
    return service.findAll(locationId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public SalesChannelResponse create(@Valid @RequestBody SaveSalesChannelRequest request) {
    return service.create(request);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public SalesChannelResponse update(@PathVariable String id, @Valid @RequestBody SaveSalesChannelRequest request) {
    return service.update(id, request);
  }
}
