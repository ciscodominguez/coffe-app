package com.cofeapp.backend.legal;

import com.cofeapp.backend.domain.ServiceRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, String> {

  boolean existsByCode(String code);

  List<ServiceRequest> findTop200ByOrderByCreatedAtDesc();

  long countByStatus(String status);
}
