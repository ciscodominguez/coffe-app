package com.cofeapp.backend.category;

import com.cofeapp.backend.category.dto.CategoryResponse;
import com.cofeapp.backend.category.dto.CreateCategoryRequest;
import com.cofeapp.backend.category.dto.UpdateCategoryRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lectura abierta a cualquier rol autenticado (mesera/cocina necesitan ver
 * el catálogo para armar/preparar pedidos en la Etapa 2); alta/edición/baja
 * restringidas a OWNER/ADMIN — mismo criterio de roles que
 * {@code LocationController}.
 */
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

  private final CategoryService categoryService;

  @GetMapping
  public List<CategoryResponse> findAll() {
    return categoryService.findAll();
  }

  @GetMapping("/{id}")
  public CategoryResponse findOne(@PathVariable String id) {
    return categoryService.findOne(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public CategoryResponse create(@Valid @RequestBody CreateCategoryRequest request) {
    return categoryService.create(request);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public CategoryResponse update(@PathVariable String id, @Valid @RequestBody UpdateCategoryRequest request) {
    return categoryService.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public void deactivate(@PathVariable String id) {
    categoryService.deactivate(id);
  }
}
