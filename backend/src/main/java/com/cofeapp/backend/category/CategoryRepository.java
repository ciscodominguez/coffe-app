package com.cofeapp.backend.category;

import com.cofeapp.backend.domain.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, String> {

  /**
   * Reemplaza a {@code findById()} (bloqueado por {@code
   * TenantSafeRepositoryImpl} para esta entidad tenant-scoped). Al ser
   * JPQL, SÍ respeta el {@code @Filter} de Hibernate habilitado para la
   * request.
   */
  @Query("select c from Category c where c.id = :id")
  Optional<Category> findByIdScoped(@Param("id") String id);

  List<Category> findAllByOrderByDisplayOrderAscNameAsc();
}
