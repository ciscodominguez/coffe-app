package com.cofeapp.backend.business;

import com.cofeapp.backend.domain.Business;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code Business} NO es tenant-scoped (es el tenant en sí), así que
 * {@code findById} funciona sin restricciones acá — ver
 * {@code TenantSafeRepositoryImpl}, que solo bloquea esa operación para
 * entidades {@code TenantScopedEntity}.
 *
 * <p>La regla de aislamiento para este módulo la aplica
 * {@code BusinessService}: el id que se consulta/actualiza SIEMPRE sale de
 * {@code tenantContext.getBusinessId()} (lo que dice el JWT), nunca de un
 * parámetro de la URL o del body — no existe un {@code GET /businesses/:id}
 * genérico.
 */
public interface BusinessRepository extends JpaRepository<Business, String> {

  /** V16 — el café de una suscripción de Mercado Pago (lo usa el cobro, sin contexto de café). */
  Optional<Business> findByMpPreapprovalId(String mpPreapprovalId);

  /** V16 — los cafés con suscripción de Mercado Pago a revisar. */
  List<Business> findAllByMpPreapprovalIdIsNotNull();
}
