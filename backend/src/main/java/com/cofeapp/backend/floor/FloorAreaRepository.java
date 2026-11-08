package com.cofeapp.backend.floor;

import com.cofeapp.backend.domain.FloorArea;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FloorAreaRepository extends JpaRepository<FloorArea, String> {

  /** Reemplaza a {@code findById()} (bloqueado para entidades de un café). */
  @Query("select a from FloorArea a where a.id = :id")
  Optional<FloorArea> findByIdScoped(@Param("id") String id);

  List<FloorArea> findAllByLocationIdOrderByPositionAscCreatedAtAsc(String locationId);

  boolean existsByLocationIdAndNameIgnoreCase(String locationId, String name);

  boolean existsByLocationIdAndNameIgnoreCaseAndIdNot(String locationId, String name, String id);
}
