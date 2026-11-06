package com.cofeapp.backend.channel;

import com.cofeapp.backend.channel.dto.SalesChannelResponse;
import com.cofeapp.backend.channel.dto.SaveSalesChannelRequest;
import com.cofeapp.backend.common.exception.BadRequestException;
import com.cofeapp.backend.common.exception.ConflictException;
import com.cofeapp.backend.common.exception.NotFoundException;
import com.cofeapp.backend.domain.SalesChannel;
import com.cofeapp.backend.domain.enums.SalesChannelKind;
import com.cofeapp.backend.domain.enums.SalesChannelMode;
import com.cofeapp.backend.location.LocationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Apps de delivery de cada local (V19, Fase A de
 * docs/INTEGRACION-APPS-DELIVERY.md). Un café sin apps no tiene canales y
 * no ve nada nuevo. Cada app se prende por separado, en cada local.
 */
@Service
@RequiredArgsConstructor
public class SalesChannelService {

  private final SalesChannelRepository repository;
  private final LocationRepository locationRepository;

  @Transactional(readOnly = true)
  public List<SalesChannelResponse> findAll(String locationId) {
    List<SalesChannel> channels =
        locationId == null || locationId.isBlank()
            ? repository.findAllByOrderByCreatedAtAsc()
            : repository.findAllByLocationIdOrderByCreatedAtAsc(locationId);
    return channels.stream().map(SalesChannelResponse::from).toList();
  }

  @Transactional
  public SalesChannelResponse create(SaveSalesChannelRequest request) {
    if (request.locationId() == null || request.locationId().isBlank()) throw new BadRequestException("Falta el local.");
    if (request.kind() == null) throw new BadRequestException("Elegí la app.");
    locationRepository
        .findByIdScoped(request.locationId())
        .orElseThrow(() -> new NotFoundException("No se encontró el local."));

    SalesChannel channel = new SalesChannel();
    channel.setLocationId(request.locationId());
    channel.setKind(request.kind());
    channel.setName(nameFor(request.kind(), request.name()));
    if (repository.existsByLocationIdAndNameIgnoreCase(request.locationId(), channel.getName())) {
      throw new ConflictException(channel.getName() + " ya está en este local.");
    }
    channel.setMode(checkMode(request.mode() == null ? SalesChannelMode.MANUAL : request.mode()));
    channel.setCommissionPct(scale(request.commissionPct()));
    return SalesChannelResponse.from(repository.save(channel));
  }

  @Transactional
  public SalesChannelResponse update(String id, SaveSalesChannelRequest request) {
    SalesChannel channel =
        repository.findByIdScoped(id).orElseThrow(() -> new NotFoundException("No se encontró esa app en el local."));
    if (request.name() != null && channel.getKind() == SalesChannelKind.OTHER) {
      String name = nameFor(SalesChannelKind.OTHER, request.name());
      if (repository.existsByLocationIdAndNameIgnoreCaseAndIdNot(channel.getLocationId(), name, id)) {
        throw new ConflictException(name + " ya está en este local.");
      }
      channel.setName(name);
    }
    if (request.mode() != null) channel.setMode(checkMode(request.mode()));
    if (request.commissionPct() != null) channel.setCommissionPct(scale(request.commissionPct()));
    return SalesChannelResponse.from(repository.save(channel));
  }

  // ---------------------------------------------------------------------

  private static String nameFor(SalesChannelKind kind, String requested) {
    if (kind.defaultName() != null) return kind.defaultName();
    String name = requested == null ? "" : requested.trim();
    if (name.isEmpty()) throw new BadRequestException("Ponele un nombre a la app.");
    return name;
  }

  private static SalesChannelMode checkMode(SalesChannelMode mode) {
    if (mode == SalesChannelMode.AUTO) {
      throw new BadRequestException("El modo automático todavía no está disponible: por ahora, usá el modo manual.");
    }
    return mode;
  }

  private static BigDecimal scale(BigDecimal pct) {
    return (pct == null ? BigDecimal.ZERO : pct).setScale(2, RoundingMode.HALF_UP);
  }
}
