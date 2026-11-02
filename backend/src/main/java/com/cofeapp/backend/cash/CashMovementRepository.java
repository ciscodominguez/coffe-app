package com.cofeapp.backend.cash;

import com.cofeapp.backend.domain.CashMovement;
import com.cofeapp.backend.domain.enums.CashMovementType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashMovementRepository extends JpaRepository<CashMovement, String> {

  List<CashMovement> findAllByCashSessionIdOrderByCreatedAtAsc(String cashSessionId);

  /** Para totalizar el efectivo esperado del turno — ver {@code CashSessionService#computeExpectedCash}. */
  List<CashMovement> findAllByCashSessionIdAndType(String cashSessionId, CashMovementType type);
}
