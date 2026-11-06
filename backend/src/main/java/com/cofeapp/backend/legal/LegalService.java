package com.cofeapp.backend.legal;

import com.cofeapp.backend.business.BusinessRepository;
import com.cofeapp.backend.common.exception.ConflictException;
import com.cofeapp.backend.common.exception.NotFoundException;
import com.cofeapp.backend.domain.Business;
import com.cofeapp.backend.domain.LegalAcceptance;
import com.cofeapp.backend.legal.dto.LegalStatusResponse;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aceptación de los términos y la política de privacidad (V17). Se guarda
 * cada aceptación (versión, quién, cuándo, IP y navegador) como constancia.
 */
@Service
@RequiredArgsConstructor
public class LegalService {

  private final LegalProperties properties;
  private final BusinessRepository businessRepository;
  private final LegalAcceptanceRepository acceptanceRepository;

  public String currentVersion() {
    return properties.version();
  }

  @Transactional(readOnly = true)
  public LegalStatusResponse status(String businessId) {
    Business business = find(businessId);
    return new LegalStatusResponse(
        properties.version(),
        business.getTermsVersion(),
        business.getTermsAcceptedAt(),
        !properties.version().equals(business.getTermsVersion()));
  }

  /** El dueño acepta la versión vigente (tiene que ser la que leyó). */
  @Transactional
  public LegalStatusResponse accept(String businessId, String userId, String version) {
    if (!properties.version().equals(version)) {
      throw new ConflictException("Hay una versión más nueva de los términos. Recargá la página y leela antes de aceptar.");
    }
    record(find(businessId), userId);
    return status(businessId);
  }

  /** Al registrarse (la casilla del formulario de alta). */
  @Transactional
  public void record(Business business, String userId) {
    Instant now = Instant.now();
    LegalAcceptance acceptance = new LegalAcceptance();
    acceptance.setBusinessId(business.getId());
    acceptance.setUserId(userId);
    acceptance.setVersion(properties.version());
    acceptance.setAcceptedAt(now);
    acceptance.setIp(ClientInfo.ip());
    acceptance.setUserAgent(ClientInfo.userAgent());
    acceptanceRepository.save(acceptance);

    business.setTermsVersion(properties.version());
    business.setTermsAcceptedAt(now);
    businessRepository.save(business);
  }

  private Business find(String businessId) {
    return businessRepository.findById(businessId).orElseThrow(() -> new NotFoundException("No se encontró el café."));
  }
}
