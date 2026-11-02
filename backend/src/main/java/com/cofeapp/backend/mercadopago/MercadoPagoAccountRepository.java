package com.cofeapp.backend.mercadopago;

import com.cofeapp.backend.domain.MercadoPagoAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MercadoPagoAccountRepository extends JpaRepository<MercadoPagoAccount, String> {

  /**
   * Filtra por {@code businessId} a mano (no solo con el filtro de
   * Hibernate) porque también lo usan el proceso de fondo y el webhook,
   * que corren sin sesión de ningún negocio.
   */
  Optional<MercadoPagoAccount> findByBusinessId(String businessId);
}
