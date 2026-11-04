package com.cofeapp.backend.platform;

import com.cofeapp.backend.domain.PlatformAction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformActionRepository extends JpaRepository<PlatformAction, String> {

  List<PlatformAction> findTop50ByBusinessIdOrderByCreatedAtDesc(String businessId);
}
