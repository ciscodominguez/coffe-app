package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.TenantFilters;
import com.cofeapp.backend.common.tenant.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * La cuenta de Mercado Pago de un negocio (V10) — una por negocio.
 *
 * <p>{@code accessTokenEncrypted}: el Access Token que pegó el dueño,
 * CIFRADO con {@link com.cofeapp.backend.common.crypto.SecretCipher}. Nunca
 * sale de este backend: ninguna respuesta de la API lo devuelve, ni
 * siquiera enmascarado.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "mp_accounts")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class MercadoPagoAccount extends TenantScopedEntity {

  @Column(name = "access_token_encrypted", nullable = false, length = 1024)
  private String accessTokenEncrypted;

  /** El id numérico de la cuenta en Mercado Pago (hace falta para crear la sucursal). */
  @Column(name = "mp_user_id", nullable = false, length = 30)
  private String mpUserId;

  @Column(name = "mp_nickname", length = 120)
  private String mpNickname;

  /** true si el token es de prueba (empieza con TEST-): los cobros no mueven plata real. */
  @Column(name = "test_mode", nullable = false)
  private Boolean testMode = false;

  @Column(name = "connected_by_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String connectedById;
}
