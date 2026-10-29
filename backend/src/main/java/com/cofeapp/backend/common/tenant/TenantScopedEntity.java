package com.cofeapp.backend.common.tenant;

import com.cofeapp.backend.common.context.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;
import com.cofeapp.backend.common.context.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * Superclase de toda entidad tenant-scoped.
 *
 * <p><b>Importante — esto por sí solo NO alcanza para el aislamiento
 * automático.</b> Hay dos partes:
 * <ol>
 *   <li>El campo {@code businessId} y su auto-asignación al crear (en
 *       {@link #assignTenantOnCreate()}).</li>
 *   <li>La entidad concreta (ver {@link com.cofeapp.backend.domain.Location}
 *       / {@link com.cofeapp.backend.domain.User}) tiene que declarar
 *       EXPLÍCITAMENTE {@code @Filter} usando {@link TenantFilters#BUSINESS_FILTER}
 *       — eso es lo que hace que Hibernate agregue {@code WHERE business_id
 *       = :businessId} a sus SELECT. La <i>definición</i> del filtro
 *       ({@code @FilterDef}) vive acá, en la superclase, una sola vez —
 *       Hibernate 6 rechaza con {@code AnnotationException} que el mismo
 *       nombre de filtro se defina en más de una entidad, aunque sean
 *       idénticas. Pero {@code @FilterDef} por sí solo NO activa nada: sigue
 *       siendo obligatorio agregar {@code @Filter} en cada entidad concreta
 *       para que el aislamiento se aplique — si te olvidás esa línea, esa
 *       entidad queda SIN aislamiento y nadie se entera.</li> * </ol>
 *
 * <p><b>Gotcha de Hibernate que hay que conocer (documentado también en
 * {@link TenantSafeRepositoryImpl}):</b> los filtros de Hibernate se
 * aplican a queries HQL/JPQL/Criteria (lo que generan los métodos
 * derivados y {@code @Query} de Spring Data) pero NO a
 * {@code EntityManager.find()}/{@code Session.get()} — que es lo que usa
 * por debajo {@code JpaRepository.findById()}. Por eso: nunca llamar
 * {@code findById()} sobre un repositorio de una entidad tenant-scoped —
 * {@link TenantSafeRepositoryImpl} lo bloquea en runtime a propósito.
 */
@Getter
@Setter
@FilterDef(
        name = TenantFilters.BUSINESS_FILTER,
        parameters = @ParamDef(name = TenantFilters.BUSINESS_ID_PARAM, type = String.class))

@MappedSuperclass
public abstract class TenantScopedEntity extends BaseEntity {

    @Column(name = "business_id", nullable = false, updatable = false, length = 36, columnDefinition = "CHAR(36)")
    private String businessId;

  /**
   * Asigna el {@code businessId} del contexto de tenant actual al crear la
   * entidad. Si el código de aplicación ya lo
   * seteó a mano (ver {@code AuthService.registerBusiness}, el único caso
   * legítimo — crea el primer {@code Location}/{@code User} de un negocio
   * recién nacido, con el contexto de tenant abierto manualmente), no lo
   * pisa.
   */
  @PrePersist
  protected void assignTenantOnCreate() {
    if (getBusinessId() == null) {
      setBusinessId(TenantContext.currentBusinessIdOrNull());
    }
  }
}
