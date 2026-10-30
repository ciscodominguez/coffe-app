package com.cofeapp.backend.product;

import com.cofeapp.backend.domain.Product;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, String> {

  /**
   * Reemplaza a {@code findById()} (bloqueado por {@code
   * TenantSafeRepositoryImpl} para esta entidad tenant-scoped).
   */
  @Query("select p from Product p where p.id = :id")
  Optional<Product> findByIdScoped(@Param("id") String id);

  /**
   * Igual que {@link #findByIdScoped} pero en lote — usado por {@code
   * StockService#restoreForOrder} para resolver el {@code stockMode} de
   * todos los productos de un pedido cancelado de una sola consulta (mismo
   * criterio que {@code UserRepository#findAllByIdInScoped}).
   */
  @Query("select p from Product p where p.id in :ids")
  List<Product> findAllByIdInScoped(@Param("ids") Collection<String> ids);

  List<Product> findAllByOrderByNameAsc();

  List<Product> findAllByCategoryIdOrderByNameAsc(String categoryId);
}
