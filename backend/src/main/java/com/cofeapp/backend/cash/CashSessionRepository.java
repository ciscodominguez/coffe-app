package com.cofeapp.backend.cash;

import com.cofeapp.backend.domain.CashSession;
import com.cofeapp.backend.domain.enums.CashSessionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashSessionRepository extends JpaRepository<CashSession, String> {

  /**
   * Reemplaza a {@code findById()} (bloqueado por {@code
   * TenantSafeRepositoryImpl} para esta entidad tenant-scoped).
   */
  @Query("select c from CashSession c where c.id = :id")
  Optional<CashSession> findByIdScoped(@Param("id") String id);

  /**
   * El turno vigente de un local, si hay uno abierto — lo usa tanto {@code
   * CashSessionController#current} como {@code OrderService#addPayment}
   * (para saber a qué turno atar cada cobro).
   */
  Optional<CashSession> findByLocationIdAndStatus(String locationId, CashSessionStatus status);

  /** Chequeo previo antes de abrir un turno — ver {@code CashSessionService#open} sobre la ventana de carrera. */
  boolean existsByLocationIdAndStatus(String locationId, CashSessionStatus status);

  List<CashSession> findAllByOrderByOpenedAtDesc();

  List<CashSession> findAllByLocationIdOrderByOpenedAtDesc(String locationId);
}
