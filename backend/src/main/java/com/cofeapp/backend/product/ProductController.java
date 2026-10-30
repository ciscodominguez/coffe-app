package com.cofeapp.backend.product;

import com.cofeapp.backend.product.dto.CreateProductRequest;
import com.cofeapp.backend.product.dto.ProductResponse;
import com.cofeapp.backend.product.dto.UpdateProductRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mismo criterio de roles que {@code CategoryController}: lectura abierta a
 * cualquier rol autenticado, escritura restringida a OWNER/ADMIN.
 */
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

  private final ProductService productService;

  /** {@code ?categoryId=...} opcional — sin él, devuelve el catálogo completo. */
  @GetMapping
  public List<ProductResponse> findAll(@RequestParam(required = false) String categoryId) {
    return categoryId != null ? productService.findAllByCategory(categoryId) : productService.findAll();
  }

  @GetMapping("/{id}")
  public ProductResponse findOne(@PathVariable String id) {
    return productService.findOne(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
    return productService.create(request);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public ProductResponse update(@PathVariable String id, @Valid @RequestBody UpdateProductRequest request) {
    return productService.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public void deactivate(@PathVariable String id) {
    productService.deactivate(id);
  }
}
