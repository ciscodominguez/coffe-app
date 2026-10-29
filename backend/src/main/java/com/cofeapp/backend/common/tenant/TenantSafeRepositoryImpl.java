package com.cofeapp.backend.common.tenant;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

/**
 * Repositorio base registrado como {@code repositoryBaseClass} para TODOS
 * los repositorios de la app (ver {@code JpaConfig}), que bloquea
 * {@code findById}/{@code getReferenceById} en runtime para cualquier
 * entidad tenant-scoped ({@link TenantScopedEntity}).
 *
 * <p>Por qué: Hibernate aplica los {@code @Filter} de aislamiento
 * multi-tenant a queries HQL/JPQL/Criteria, pero NO al acceso directo por
 * clave primaria vía {@code EntityManager.find()}/{@code Session.get()} —
 * que es lo que usan por debajo {@code findById()}/{@code
 * getReferenceById()} de Spring Data. Dejarlos "colados" permitiría leer
 * (o referenciar para un update en cascada) una fila de OTRO negocio con
 * solo adivinar/probar un id — literalmente el bug de aislamiento que este
 * proyecto existe para evitar.
 *
 * <p>El error que se tira explica la alternativa correcta (ver abajo).
 *
 * <p>Las entidades NO tenant-scoped (ej. {@code Business}, que es el
 * tenant en sí — no tiene {@code businessId}) pasan de largo sin
 * restricción: {@code findById} sobre {@code BusinessRepository} sigue
 * funcionando normalmente.
 *
 * <p>Alternativa correcta para una entidad tenant-scoped: un método propio
 * del repositorio basado en {@code @Query} (JPQL) — ver
 * {@code UserRepository#findByIdScoped}/{@code LocationRepository#findByIdScoped}.
 * Un JPQL como {@code select u from User u where u.id = :id} SÍ pasa por
 * el filtro activo (el filtro reescribe el SQL generado, no el JPQL en
 * sí), así que agrega el aislamiento sin que el método lo mencione.
 */
public class TenantSafeRepositoryImpl<T, ID> extends SimpleJpaRepository<T, ID> {

  private final boolean tenantScoped;
  private final Class<T> domainClass;

  public TenantSafeRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
    super(entityInformation, entityManager);
    this.domainClass = entityInformation.getJavaType();
    this.tenantScoped = TenantScopedEntity.class.isAssignableFrom(domainClass);
  }

  @Override
  public Optional<T> findById(ID id) {
    assertNotTenantScoped("findById");
    return super.findById(id);
  }

  @Override
  public T getReferenceById(ID id) {
    assertNotTenantScoped("getReferenceById");
    return super.getReferenceById(id);
  }

  // Nota: `getOne(ID)` (alias viejo de `getReferenceById`) fue removido de
  // Spring Data JPA en la serie 3.x que usa este proyecto — no hay nada
  // que sobreescribir acá para ese método en Spring Boot 3.

  private void assertNotTenantScoped(String operation) {
    if (tenantScoped) {
      throw new UnsupportedOperationException(
          operation
              + "() sobre "
              + domainClass.getSimpleName()
              + " está bloqueado a propósito: es una entidad tenant-scoped y Hibernate NO aplica "
              + "el @Filter de aislamiento multi-tenant a EntityManager.find()/Session.get() (lo "
              + "que "
              + operation
              + "() usa por debajo) — carga la fila por su clave primaria sin pasar por el WHERE "
              + "que agrega el filtro. Usá el método *Scoped(...) del repositorio concreto, "
              + "basado en @Query (JPQL) — esas sí lo respetan. Ver TenantScopedEntity para el "
              + "detalle completo.");
    }
  }
}
