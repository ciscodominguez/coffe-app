package com.cofeapp.backend.billing;

import com.cofeapp.backend.domain.PlatformSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, String> {}
