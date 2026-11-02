package com.cofeapp.backend.cash;

import com.cofeapp.backend.domain.CashSessionDenomination;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashSessionDenominationRepository extends JpaRepository<CashSessionDenomination, String> {

  List<CashSessionDenomination> findAllByCashSessionIdOrderByDenominationDesc(String cashSessionId);
}
