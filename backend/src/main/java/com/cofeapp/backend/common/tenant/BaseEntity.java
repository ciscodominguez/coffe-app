package com.cofeapp.backend.common.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Campos comunes a toda entidad del dominio: id, createdAt, updatedAt.
 *
 * El id es un UUID de TEXTO (String), no el tipo UUID nativo de la base:
 * evita fricción entre drivers/dialectos y hace
 * que el mismo valor de id viaje sin conversión por JSON, JWT y URL.
 * Generado en la aplicación (acá, en {@link #assignIdAndTimestamps()}),
 * nunca por la base — evita ids secuenciales adivinables entre tenants
 * (mitigación de IDOR).
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @Column(length = 36, updatable = false, nullable = false, columnDefinition = "CHAR(36)")
    private String id;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  protected void assignIdAndTimestamps() {
    if (this.id == null) {
      this.id = UUID.randomUUID().toString();
    }
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  protected void touchUpdatedAt() {
    this.updatedAt = Instant.now();
  }
}
