package com.cofeapp.backend.business.dto;

import com.cofeapp.backend.domain.enums.FiscalCondition;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code taxId} (CUIT), {@code plan} y {@code status} quedan afuera a
 * propósito: cambiar el CUIT es un evento legal excepcional (no
 * self-service), y {@code plan}/{@code status} los controla la plataforma
 * (facturación/soporte, Etapa 5) — no el dueño del negocio.
 */
@Getter
@Setter
public class UpdateBusinessRequest {

  @Size(min = 2, max = 150)
  private String legalName;

  @Size(max = 150)
  private String tradeName;

  private FiscalCondition fiscalCondition;
}
