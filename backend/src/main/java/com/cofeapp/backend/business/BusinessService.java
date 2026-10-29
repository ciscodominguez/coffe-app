package com.cofeapp.backend.business;

import com.cofeapp.backend.business.dto.BusinessDisplayNameResponse;
import com.cofeapp.backend.business.dto.BusinessResponse;
import com.cofeapp.backend.business.dto.UpdateBusinessRequest;
import com.cofeapp.backend.common.context.TenantContext;
import com.cofeapp.backend.common.exception.NotFoundException;
import com.cofeapp.backend.domain.Business;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code Business} NO es tenant-scoped: es el tenant en sí, no un registro
 * dentro de un tenant. La regla de aislamiento acá la aplica este service,
 * no un {@code @Filter} de Hibernate: el id que se consulta/actualiza
 * SIEMPRE sale de {@code tenantContext.getBusinessId()} (lo que dice el
 * JWT), nunca de un parámetro de la URL o del body — por diseño no existe
 * un {@code GET /businesses/:id} genérico, que es lo que le impide a un
 * negocio leer o tocar a otro.
 */
@Service
@RequiredArgsConstructor
public class BusinessService {

  private final BusinessRepository businessRepository;
  private final TenantContext tenantContext;

  public BusinessResponse findMe() {
    return BusinessResponse.from(findOwnBusinessOrThrow());
  }

  public BusinessDisplayNameResponse findMyDisplayName() {
    return BusinessDisplayNameResponse.from(findOwnBusinessOrThrow());
  }

  @Transactional
  public BusinessResponse updateMe(UpdateBusinessRequest request) {
    Business business = findOwnBusinessOrThrow();

    if (request.getLegalName() != null) business.setLegalName(request.getLegalName());
    if (request.getTradeName() != null) business.setTradeName(request.getTradeName());
    if (request.getFiscalCondition() != null) business.setFiscalCondition(request.getFiscalCondition());

    return BusinessResponse.from(businessRepository.save(business));
  }

  private Business findOwnBusinessOrThrow() {
    return businessRepository
        .findById(tenantContext.getBusinessId())
        .orElseThrow(() -> new NotFoundException("No se encontró el negocio del token actual."));
  }
}
