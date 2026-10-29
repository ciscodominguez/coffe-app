package com.cofeapp.backend.user;

import com.cofeapp.backend.domain.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, String> {

  /**
   * Reemplaza a {@code findById()} (bloqueado por {@code
   * TenantSafeRepositoryImpl} para esta entidad tenant-scoped) — ver el
   * mismo comentario en {@code LocationRepository}.
   */
  @Query("select u from User u where u.id = :id")
  Optional<User> findByIdScoped(@Param("id") String id);

  /**
   * Igual que {@link #findByIdScoped} pero en lote — usado por {@code
   * CashSessionService}/{@code OrderService} para resolver "nombre real"
   * (quién abrió/cerró un turno, quién cargó cada movimiento/cobro) sin
   * disparar una query por usuario. Ver {@code
   * CashSessionService#resolveNames}.
   */
  @Query("select u from User u where u.id in :ids")
  List<User> findAllByIdInScoped(@Param("ids") Collection<String> ids);

  /**
   * Usado SOLO por el login (`AuthService`). {@code email} es único a
   * nivel global, así que esta búsqueda es deliberadamente "cruda": en el
   * momento del login todavía no hay contexto de tenant abierto (es
   * justamente lo que este método está por descubrir), así que el filtro
   * de Hibernate está deshabilitado y esta query corre sin ningún
   * {@code WHERE business_id = ...}.
   */
  Optional<User> findByEmail(String email);

  List<User> findAllByOrderByCreatedAtAsc();

  List<User> findAllByIsActiveTrueOrderByCreatedAtAsc();
}
