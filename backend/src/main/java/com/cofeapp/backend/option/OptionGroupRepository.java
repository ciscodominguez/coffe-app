package com.cofeapp.backend.option;

import com.cofeapp.backend.domain.OptionGroup;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OptionGroupRepository extends JpaRepository<OptionGroup, String> {

  /** Reemplaza a {@code findById()} (bloqueado por {@code TenantSafeRepositoryImpl} para esta entidad tenant-scoped). */
  @Query("select g from OptionGroup g where g.id = :id")
  Optional<OptionGroup> findByIdScoped(@Param("id") String id);

  @Query("select g from OptionGroup g where g.id in :ids order by g.position asc, g.createdAt asc")
  List<OptionGroup> findAllByIdInScoped(@Param("ids") Collection<String> ids);

  List<OptionGroup> findAllByOrderByPositionAscCreatedAtAsc();

  @Query("select coalesce(max(g.position), -1) from OptionGroup g")
  int findMaxPosition();

  /** Para avisar "ya existe un grupo con ese nombre" antes de chocar contra el índice único. */
  @Query("select g from OptionGroup g where lower(g.name) = lower(:name)")
  List<OptionGroup> findAllByNameIgnoreCaseScoped(@Param("name") String name);
}
