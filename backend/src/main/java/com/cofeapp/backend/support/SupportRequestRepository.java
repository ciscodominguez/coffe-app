package com.cofeapp.backend.support;

import com.cofeapp.backend.domain.SupportRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportRequestRepository extends JpaRepository<SupportRequest, String> {

  List<SupportRequest> findTop200ByOrderByCreatedAtDesc();

  long countByUserIdAndCreatedAtAfter(String userId, Instant since);
}
