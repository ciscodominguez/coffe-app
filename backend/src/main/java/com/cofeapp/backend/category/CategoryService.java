package com.cofeapp.backend.category;

import com.cofeapp.backend.category.dto.CategoryResponse;
import com.cofeapp.backend.category.dto.CreateCategoryRequest;
import com.cofeapp.backend.category.dto.UpdateCategoryRequest;
import com.cofeapp.backend.common.exception.NotFoundException;
import com.cofeapp.backend.domain.Category;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

  private final CategoryRepository categoryRepository;

  public List<CategoryResponse> findAll() {
    return categoryRepository.findAllByOrderByDisplayOrderAscNameAsc().stream()
        .map(CategoryResponse::from)
        .toList();
  }

  public CategoryResponse findOne(String id) {
    return CategoryResponse.from(findOneOrThrow(id));
  }

  @Transactional
  public CategoryResponse create(CreateCategoryRequest request) {
    Category category = new Category();
    category.setName(request.getName());
    category.setDescription(request.getDescription());
    category.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);
    return CategoryResponse.from(categoryRepository.save(category));
  }

  @Transactional
  public CategoryResponse update(String id, UpdateCategoryRequest request) {
    Category category = findOneOrThrow(id);

    if (request.getName() != null) category.setName(request.getName());
    if (request.getDescription() != null) category.setDescription(request.getDescription());
    if (request.getDisplayOrder() != null) category.setDisplayOrder(request.getDisplayOrder());
    if (request.getIsActive() != null) category.setIsActive(request.getIsActive());

    return CategoryResponse.from(categoryRepository.save(category));
  }

  /**
   * Baja lógica ({@code isActive = false}), nunca un DELETE físico — mismo
   * criterio que {@code fk_products_category ON DELETE RESTRICT} en la
   * migración: una categoría ya usada por productos no puede desaparecer
   * sin romper esa referencia.
   */
  @Transactional
  public void deactivate(String id) {
    Category category = findOneOrThrow(id);
    category.setIsActive(false);
    categoryRepository.save(category);
  }

  private Category findOneOrThrow(String id) {
    // findByIdScoped (JPQL), no findById: ver TenantScopedEntity/
    // TenantSafeRepositoryImpl.
    return categoryRepository
        .findByIdScoped(id)
        .orElseThrow(() -> new NotFoundException("No se encontró la categoría " + id + "."));
  }
}
