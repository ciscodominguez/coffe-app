package com.cofeapp.backend.stock;

import com.cofeapp.backend.domain.ProductRecipeItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ProductRecipeItemRepository extends JpaRepository<ProductRecipeItem, String> {

  List<ProductRecipeItem> findAllByProductId(String productId);

  /** Usado al descontar/reponer stock de un pedido — ver {@code StockService#consumeForOrder}. */
  List<ProductRecipeItem> findAllByProductIdIn(List<String> productIds);

  /** {@code StockService#replaceRecipe} borra todo antes de insertar la receta nueva — ver el Javadoc de {@code ProductRecipeItem}. */
  @Transactional
  void deleteAllByProductId(String productId);
}
