package com.cofeapp.backend.common.context;

import org.springframework.stereotype.Component;

/**
 * Contexto de tenant por-request.
 *
 * Se guarda en un {@link ThreadLocal} — no hace falta nada más
 * sofisticado: Spring MVC usa un modelo de "un hilo por request" sobre
 * Tomcat, así que un
 * {@code ThreadLocal} normal ya garantiza que cada request tenga su propio
 * valor aislado sin que se mezcle con el de otra request concurrente.
 *
 * <p>{@link com.cofeapp.backend.common.security.JwtAuthenticationFilter} es
 * quien llama a {@link #open} una vez por request, inmediatamente después
 * de validar el JWT — y a {@link #clear} en un {@code finally}, para que un
 * hilo reciclado por el pool de Tomcat nunca arrastre el contexto de una
 * request anterior.
 */
@Component
public class TenantContext {

  /** Datos del tenant de la request actual (negocio, local, usuario, rol y email). */
  public record TenantStore(String businessId, String locationId, String userId, String role, String email) {}

  private static final ThreadLocal<TenantStore> STORAGE = new ThreadLocal<>();

  public void open(TenantStore store) {
    STORAGE.set(store);
  }

  /** Debe llamarse siempre en un {@code finally}, aunque el request haya fallado. */
  public void clear() {
    STORAGE.remove();
  }

  /** true si hay un contexto de tenant activo en el hilo actual. */
  public boolean isActive() {
    return STORAGE.get() != null;
  }

  private TenantStore getStoreOrThrow() {
    TenantStore store = STORAGE.get();
    if (store == null) {
      throw new IllegalStateException(
          "TenantContext: no hay contexto de tenant activo. Esto pasa si se accede a "
              + "businessId/locationId fuera de una request HTTP autenticada (por ejemplo, en un "
              + "script de seed o un job programado). Para esos casos, no uses un repositorio de "
              + "una entidad tenant-scoped desde ese código sin abrir el contexto a mano primero "
              + "(ver AuthService.registerBusiness para un ejemplo de cómo hacerlo).");
    }
    return store;
  }

  public String getBusinessId() {
    return getStoreOrThrow().businessId();
  }

  /** Nulo si el usuario tiene acceso a todos los locales del negocio. */
  public String getLocationId() {
    return getStoreOrThrow().locationId();
  }

  public String getUserId() {
    return getStoreOrThrow().userId();
  }

  public String getRole() {
    return getStoreOrThrow().role();
  }

  public String getEmail() {
    return getStoreOrThrow().email();
  }

  /**
   * Accesor ESTÁTICO, de uso exclusivo para
   * {@link com.cofeapp.backend.common.tenant.TenantScopedEntity#assignTenantOnCreate()}.
   *
   * <p>Un {@code @PrePersist} de JPA corre sobre la entidad misma, que no
   * es un bean de Spring — no hay forma de inyectarle este componente por
   * constructor. Como el {@link ThreadLocal} de arriba ya es {@code
   * static} (es lo que lo hace compartido por todas las instancias de este
   * bean, que en la práctica es un singleton), exponer un acceso estático
   * de solo lectura acá no agrega estado nuevo — sigue siendo el mismo
   * almacenamiento que usan {@link #open}/{@link #clear}/{@link
   * #getBusinessId()} vía las instancias inyectadas en el resto de la app
   * (filtro JWT, interceptor de tenant, services). Nunca lo uses fuera de
   * ese caso: en cualquier service/controller/filter, siempre inyectá
   * {@code TenantContext} normalmente, como cualquier otro bean.
   */
  public static String currentBusinessIdOrNull() {
    TenantStore store = STORAGE.get();
    return store == null ? null : store.businessId();
  }
}
