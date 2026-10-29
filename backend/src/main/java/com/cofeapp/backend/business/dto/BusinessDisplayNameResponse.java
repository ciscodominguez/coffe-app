package com.cofeapp.backend.business.dto;

import com.cofeapp.backend.domain.Business;

/**
 * Vista mínima del negocio para cualquier rol autenticado — ver el Javadoc
 * de {@code BusinessController}. Nunca agregar acá datos fiscales ni de
 * plan: para eso está {@link BusinessResponse}, que es solo de gestión.
 *
 * @param displayName nombre de fantasía si existe; si no, la razón social.
 */
public record BusinessDisplayNameResponse(String displayName) {

  public static BusinessDisplayNameResponse from(Business business) {
    String tradeName = business.getTradeName();
    return new BusinessDisplayNameResponse(
        tradeName != null && !tradeName.isBlank() ? tradeName : business.getLegalName());
  }
}
