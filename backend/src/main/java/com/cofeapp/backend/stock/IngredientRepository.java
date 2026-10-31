package com.cofeapp.backend.stock;

import com.cofeapp.backend.domain.Ingredient;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IngredientRepository extends JpaRepository<Ingredient, String> {

  /** Reemplaza a {@code findById()} (bloqueado por {@code TenantSafeRepositoryImpl} para esta entidad tenant-scoped). */
  @Query("select i from Ingredient i where i.id = :id")
  Optional<Ingredient> findByIdScoped(@Param("id") String id);

  List<Ingredient> findAllByOrderByNameAsc();

  /** Para la pantalla de consulta de niveles — solo insumos vigentes, no dados de baja. */
  List<Ingredient> findAllByIsActiveTrueOrderByNameAsc();
}
