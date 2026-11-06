package com.cofeapp.backend.platform;

import com.cofeapp.backend.domain.PlatformAdmin;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, String> {

  Optional<PlatformAdmin> findByEmail(String email);
}
