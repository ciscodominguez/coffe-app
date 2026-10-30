package com.cofeapp.backend.stock;

import com.cofeapp.backend.domain.IngredientStockAdjustment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientStockAdjustmentRepository extends JpaRepository<IngredientStockAdjustment, String> {

  List<IngredientStockAdjustment> findAllByIngredientIdAndLocationIdOrderByCreatedAtDesc(
      String ingredientId, String locationId);
}
