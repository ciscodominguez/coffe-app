package com.cofeapp.backend.table;

import com.cofeapp.backend.domain.RestaurantTable;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TableRepository extends JpaRepository<RestaurantTable, String> {

  /**
   * Reemplaza a {@code findById()} (bloqueado por {@code
   * TenantSafeRepositoryImpl} para esta entidad tenant-scoped).
   */
  @Query("select t from RestaurantTable t where t.id = :id")
  Optional<RestaurantTable> findByIdScoped(@Param("id") String id);

  /**
   * Misma búsqueda que {@link #findByIdScoped}, pero con lock pesimista
   * (SELECT ... FOR UPDATE) — la usa {@code OrderService#create} para
   * serializar dos creaciones concurrentes sobre la MISMA mesa, de forma
   * que el chequeo de "mesa libre" que sigue no tenga ventana de carrera.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from RestaurantTable t where t.id = :id")
  Optional<RestaurantTable> findByIdScopedForUpdate(@Param("id") String id);

  List<RestaurantTable> findAllByOrderByNumberAsc();

  List<RestaurantTable> findAllByLocationIdOrderByNumberAsc(String locationId);

  /** Para validar el UNIQUE KEY {@code uq_tables_location_number} antes de insertar — ver {@code TableService}. */
  boolean existsByLocationIdAndNumber(String locationId, Integer number);

  /** Misma validación que arriba, pero al actualizar (excluye la propia mesa del chequeo). */
  boolean existsByLocationIdAndNumberAndIdNot(String locationId, Integer number, String id);
}
