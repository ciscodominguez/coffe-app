package com.cofeapp.backend.legal;

import com.cofeapp.backend.domain.LegalAcceptance;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalAcceptanceRepository extends JpaRepository<LegalAcceptance, String> {

  List<LegalAcceptance> findAllByBusinessIdOrderByAcceptedAtDesc(String businessId);
}
