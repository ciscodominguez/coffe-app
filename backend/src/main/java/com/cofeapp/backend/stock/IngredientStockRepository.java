package com.cofeapp.backend.stock;

import com.cofeapp.backend.domain.IngredientStock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientStockRepository extends JpaRepository<IngredientStock, String> {

  Optional<IngredientStock> findByIngredientIdAndLocationId(String ingredientId, String locationId);

  /** Igual que {@code ProductStockRepository#insertIfAbsent}. */
  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      value =
          "INSERT IGNORE INTO ingredient_stocks (id, business_id, ingredient_id, location_id, current_stock, created_at, updated_at)"
              + " VALUES (UUID(), :businessId, :ingredientId, :locationId, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
      nativeQuery = true)
  int insertIfAbsent(
      @org.springframework.data.repository.query.Param("businessId") String businessId,
      @org.springframework.data.repository.query.Param("ingredientId") String ingredientId,
      @org.springframework.data.repository.query.Param("locationId") String locationId);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select s from IngredientStock s where s.ingredientId = :ingredientId and s.locationId = :locationId")
  Optional<IngredientStock> lockByIngredientIdAndLocationId(
      @org.springframework.data.repository.query.Param("ingredientId") String ingredientId,
      @org.springframework.data.repository.query.Param("locationId") String locationId);

  /** Para armar la pantalla de niveles de un local de una — todos los insumos que ya tienen fila ahí. */
  List<IngredientStock> findAllByLocationId(String locationId);
}
