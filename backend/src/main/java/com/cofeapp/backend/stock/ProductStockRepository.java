package com.cofeapp.backend.stock;

import com.cofeapp.backend.domain.ProductStock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductStockRepository extends JpaRepository<ProductStock, String> {

  Optional<ProductStock> findByProductIdAndLocationId(String productId, String locationId);

  /**
   * Crea la fila en cero si todavía no existe (si dos ventas llegan juntas,
   * la segunda no falla: INSERT IGNORE). Ver {@code StockService#lockedProductStock}.
   */
  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      value =
          "INSERT IGNORE INTO product_stocks (id, business_id, product_id, location_id, current_stock, created_at, updated_at)"
              + " VALUES (UUID(), :businessId, :productId, :locationId, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
      nativeQuery = true)
  int insertIfAbsent(
      @org.springframework.data.repository.query.Param("businessId") String businessId,
      @org.springframework.data.repository.query.Param("productId") String productId,
      @org.springframework.data.repository.query.Param("locationId") String locationId);

  /** La fila de stock bloqueada hasta terminar la venta (SELECT ... FOR UPDATE). */
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select s from ProductStock s where s.productId = :productId and s.locationId = :locationId")
  Optional<ProductStock> lockByProductIdAndLocationId(
      @org.springframework.data.repository.query.Param("productId") String productId,
      @org.springframework.data.repository.query.Param("locationId") String locationId);

  List<ProductStock> findAllByLocationId(String locationId);
}
