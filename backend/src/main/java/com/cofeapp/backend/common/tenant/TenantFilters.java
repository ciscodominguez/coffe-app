package com.cofeapp.backend.common.tenant;

/**
 * Nombre del filtro de Hibernate y de su parámetro, centralizados acá para
 * que las anotaciones {@code @FilterDef}/{@code @Filter} de cada entidad
 * tenant-scoped y {@link TenantFilterInterceptor} (que lo habilita por
 * request) usen siempre el mismo valor — evita un typo entre un archivo y
 * otro que dejaría una entidad silenciosamente sin aislamiento.
 */
public final class TenantFilters {

  public static final String BUSINESS_FILTER = "businessScopeFilter";
  public static final String BUSINESS_ID_PARAM = "businessId";

  private TenantFilters() {}
}
