package com.cofeapp.backend.common.tenant;

import com.cofeapp.backend.common.context.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Session;
import org.springframework.core.Ordered;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Habilita el filtro de Hibernate de aislamiento multi-tenant una vez por
 * request, ANTES de que corra el controller. La pieza que efectivamente
 * filtra las queries es el {@code @Filter} de Hibernate (ver {@link
 * TenantFilters}); este interceptor solo lo activa.
 *
 * <p>Solo hace algo si {@link TenantContext#isActive()} — es decir, si
 * {@code JwtAuthenticationFilter} ya validó un JWT para esta request y
 * abrió el contexto. En una ruta pública (login, alta de negocio) no hay
 * contexto abierto, así que el filtro de Hibernate queda deshabilitado y
 * las queries corren "crudas", que es justo lo que necesitan esos dos
 * casos (todavía no hay tenant).
 *
 * <p><b>Cómo se engancha con la sesión correcta (bug real, encontrado por
 * los tests de aislamiento):</b> con {@code spring.jpa.open-in-view=true},
 * Spring Boot registra {@code OpenEntityManagerInViewInterceptor} — un
 * interceptor de Spring MVC, NO un filtro de Servlet — que ata un
 * {@code EntityManager} al hilo de la request en su propio
 * {@code preHandle}. Antes, este interceptor corría PRIMERO y habilitaba el
 * filtro sobre un {@code EntityManager} temporal que se cerraba al toque:
 * el filtro se perdía y las listas traían datos de todos los negocios.
 *
 * <p>Ahora:
 * <ol>
 *   <li>{@link com.cofeapp.backend.config.WebMvcConfig} lo registra con
 *       orden {@link #ORDER}, después del de open-in-view (orden 0).</li>
 *   <li>Toma el {@code EntityManager} realmente atado a la request (no el
 *       proxy compartido) y, si no hay ninguno, corta la request con error
 *       en vez de seguir sin aislamiento. Falla cerrado: mejor un 500 que
 *       mostrarle a un café los datos de otro.</li>
 * </ol>
 */
@Component
public class TenantFilterInterceptor implements HandlerInterceptor {

  /** Después de OpenEntityManagerInViewInterceptor, que queda en orden 0. */
  public static final int ORDER = Ordered.LOWEST_PRECEDENCE;

  private final EntityManagerFactory entityManagerFactory;
  private final TenantContext tenantContext;

  public TenantFilterInterceptor(EntityManagerFactory entityManagerFactory, TenantContext tenantContext) {
    this.entityManagerFactory = entityManagerFactory;
    this.tenantContext = tenantContext;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (tenantContext.isActive()) {
      EntityManager entityManager = EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
      if (entityManager == null) {
        throw new IllegalStateException(
            "No hay un EntityManager atado a esta request, así que no se puede aplicar el aislamiento"
                + " entre negocios. Revisá que spring.jpa.open-in-view siga en true y que"
                + " TenantFilterInterceptor esté registrado después de open-in-view.");
      }
      entityManager
          .unwrap(Session.class)
          .enableFilter(TenantFilters.BUSINESS_FILTER)
          .setParameter(TenantFilters.BUSINESS_ID_PARAM, tenantContext.getBusinessId());
    }
    return true;
  }
}
