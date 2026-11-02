package com.cofeapp.backend.mercadopago;

import com.cofeapp.backend.domain.MpQrCharge;
import com.cofeapp.backend.domain.enums.QrChargeStatus;
import com.cofeapp.backend.domain.enums.QrMode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MpQrChargeRepository extends JpaRepository<MpQrCharge, String> {

  /** Reemplaza a {@code findById()} (bloqueado para entidades por negocio). Respeta el filtro de negocio. */
  @Query("select c from MpQrCharge c where c.id = :id")
  Optional<MpQrCharge> findByIdScoped(@Param("id") String id);

  Optional<MpQrCharge> findFirstByOrderIdAndStatusOrderByCreatedAtDesc(String orderId, QrChargeStatus status);

  boolean existsByOrderIdAndStatus(String orderId, QrChargeStatus status);

  boolean existsByLocationIdAndStatus(String locationId, QrChargeStatus status);

  Optional<MpQrCharge> findFirstByLocationIdAndModeAndStatus(String locationId, QrMode mode, QrChargeStatus status);

  boolean existsByBusinessIdAndStatus(String businessId, QrChargeStatus status);

  /** Webhook: Mercado Pago avisa por el id de SU orden. */
  Optional<MpQrCharge> findByMpOrderId(String mpOrderId);

  /** Proceso de fondo: los pendientes más viejos primero, de todos los negocios. */
  List<MpQrCharge> findTop50ByStatusOrderByCreatedAtAsc(QrChargeStatus status);
}
