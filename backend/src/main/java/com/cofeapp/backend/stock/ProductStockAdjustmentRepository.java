package com.cofeapp.backend.stock;

import com.cofeapp.backend.domain.ProductStockAdjustment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductStockAdjustmentRepository extends JpaRepository<ProductStockAdjustment, String> {

  List<ProductStockAdjustment> findAllByProductIdAndLocationIdOrderByCreatedAtDesc(String productId, String locationId);
}
