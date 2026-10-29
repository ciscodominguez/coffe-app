package com.cofeapp.backend.location;

import com.cofeapp.backend.domain.Location;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocationRepository extends JpaRepository<Location, String> {

  /**
   * Reemplaza a {@code findById()} (bloqueado por {@code
   * TenantSafeRepositoryImpl} para esta entidad tenant-scoped). Al ser
   * JPQL, SÍ respeta el {@code @Filter} de Hibernate habilitado para la
   * request — nunca hace falta agregar {@code businessId} a mano acá.
   */
  @Query("select l from Location l where l.id = :id")
  Optional<Location> findByIdScoped(@Param("id") String id);

  List<Location> findAllByOrderByCreatedAtAsc();

  /**
   * Única consulta de este repositorio que filtra por {@code businessId} a
   * mano en vez de apoyarse en el {@code @Filter} de Hibernate — la usa
   * {@code WebSocketAuthHandshakeInterceptor} para resolver el local por
   * defecto de un socket OWNER/ADMIN durante el handshake de WebSocket, que
   * corre por fuera de la cadena de {@code HandlerInterceptor} de Spring
   * MVC (no hay filtro de tenant habilitado ni contexto en el {@code
   * ThreadLocal} de {@code TenantContext} en ese punto). Como acá el
   * {@code businessId} ya viene validado desde el JWT, filtrarlo
   * explícito como parámetro es tan seguro como el filtro ambiental, sin
   * depender de él.
   */
  Optional<Location> findFirstByBusinessIdOrderByCreatedAtAsc(String businessId);
}
