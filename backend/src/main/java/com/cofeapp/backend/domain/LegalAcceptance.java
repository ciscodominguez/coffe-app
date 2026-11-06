package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una aceptación de los términos y la política de privacidad (V17): qué versión, quién, cuándo y desde dónde. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "legal_acceptances")
public class LegalAcceptance extends BaseEntity {

  @Column(name = "business_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String businessId;

  @Column(name = "user_id", length = 36, columnDefinition = "CHAR(36)")
  private String userId;

  @Column(nullable = false, length = 20)
  private String version;

  @Column(name = "accepted_at", nullable = false)
  private Instant acceptedAt;

  @Column(length = 64)
  private String ip;

  @Column(name = "user_agent", length = 255)
  private String userAgent;
}
