package com.cofeapp.backend.product;

import com.cofeapp.backend.category.CategoryRepository;
import com.cofeapp.backend.common.exception.NotFoundException;
import com.cofeapp.backend.domain.Product;
import com.cofeapp.backend.product.dto.CreateProductRequest;
import com.cofeapp.backend.product.dto.ProductResponse;
import com.cofeapp.backend.product.dto.UpdateProductRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;

  public List<ProductResponse> findAll() {
    return productRepository.findAllByOrderByNameAsc().stream().map(ProductResponse::from).toList();
  }

  public List<ProductResponse> findAllByCategory(String categoryId) {
    // Valida que la categoría exista (y sea del negocio actual, vía el
    // filtro de Hibernate) antes de devolver una lista que si no podría
    // simplemente estar vacía por un id mal tipeado, sin avisar del error.
    assertCategoryExists(categoryId);
    return productRepository.findAllByCategoryIdOrderByNameAsc(categoryId).stream()
        .map(ProductResponse::from)
        .toList();
  }

  public ProductResponse findOne(String id) {
    return ProductResponse.from(findOneOrThrow(id));
  }

  @Transactional
  public ProductResponse create(CreateProductRequest request) {
    assertCategoryExists(request.getCategoryId());

    Product product = new Product();
    product.setCategoryId(request.getCategoryId());
    product.setName(request.getName());
    product.setDescription(request.getDescription());
    product.setIngredients(request.getIngredients());
    product.setSku(request.getSku());
    product.setPrice(request.getPrice());
    product.setImageUrl(request.getImageUrl());

    return ProductResponse.from(productRepository.save(product));
  }

  @Transactional
  public ProductResponse update(String id, UpdateProductRequest request) {
    Product product = findOneOrThrow(id);

    if (request.getCategoryId() != null) {
      assertCategoryExists(request.getCategoryId());
      product.setCategoryId(request.getCategoryId());
    }
    if (request.getName() != null) product.setName(request.getName());
    if (request.getDescription() != null) product.setDescription(request.getDescription());
    if (request.getIngredients() != null) product.setIngredients(request.getIngredients());
    if (request.getSku() != null) product.setSku(request.getSku());
    if (request.getPrice() != null) product.setPrice(request.getPrice());
    if (request.getImageUrl() != null) product.setImageUrl(request.getImageUrl());
    if (request.getIsActive() != null) product.setIsActive(request.getIsActive());

    return ProductResponse.from(productRepository.save(product));
  }

  /**
   * Baja lógica ({@code isActive = false}), nunca un DELETE físico — ver el
   * comentario de {@code fk_products_category ON DELETE RESTRICT} en la
   * migración: un producto ya vendido no puede desaparecer sin romper el
   * historial de {@code order_items} que lo referencian (Bloque 3).
   */
  @Transactional
  public void deactivate(String id) {
    Product product = findOneOrThrow(id);
    product.setIsActive(false);
    productRepository.save(product);
  }

  private void assertCategoryExists(String categoryId) {
    categoryRepository
        .findByIdScoped(categoryId)
        .orElseThrow(() -> new NotFoundException("No se encontró la categoría " + categoryId + "."));
  }

  private Product findOneOrThrow(String id) {
    return productRepository
        .findByIdScoped(id)
        .orElseThrow(() -> new NotFoundException("No se encontró el producto " + id + "."));
  }
}
