package com.cofeapp.backend.stock;

import com.cofeapp.backend.product.dto.ProductResponse;
import com.cofeapp.backend.stock.dto.AdjustStockRequest;
import com.cofeapp.backend.stock.dto.CreateIngredientRequest;
import com.cofeapp.backend.stock.dto.IngredientResponse;
import com.cofeapp.backend.stock.dto.IngredientStockLevelResponse;
import com.cofeapp.backend.stock.dto.ProductRecipeItemResponse;
import com.cofeapp.backend.stock.dto.ProductStockLevelResponse;
import com.cofeapp.backend.stock.dto.StockAdjustmentResponse;
import com.cofeapp.backend.stock.dto.UpdateIngredientRequest;
import com.cofeapp.backend.stock.dto.UpdateProductStockConfigRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * Bloque D (ítems 12/13). Dos niveles de permiso, a pedido explícito de
 * Fran ("gerencia carga, todos consultan"):
 *
 * <ul>
 *   <li>Lectura de niveles de stock ({@code /levels}) y del catálogo de
 *       insumos ({@code GET /ingredients}) — SIN {@code @PreAuthorize}:
 *       abierto a cualquier rol autenticado, mismo criterio que {@code
 *       ProductController}/{@code CategoryController} (WAITER/KITCHEN
 *       incluidos, para que puedan ver si algo se está por acabar).
 *   <li>Todo lo demás (alta/edición de insumos, configurar el stock de un
 *       producto, ajustes manuales, historial de ajustes) — {@code
 *       MANAGEMENT} (OWNER/ADMIN/SUPERVISOR), igual que Negocio/Usuarios/
 *       Locales/Categorías.
 * </ul>
 */
@RestController
@RequestMapping("/stock")
@RequiredArgsConstructor
public class StockController {

  private static final String MANAGEMENT = "hasAnyAuthority('OWNER', 'ADMIN', 'SUPERVISOR')";

  private final StockService stockService;

  // ---- Insumos ----

  @GetMapping("/ingredients")
  public List<IngredientResponse> findAllIngredients() {
    return stockService.findAllIngredients();
  }

  @PostMapping("/ingredients")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(MANAGEMENT)
  public IngredientResponse createIngredient(@Valid @RequestBody CreateIngredientRequest request) {
    return stockService.createIngredient(request);
  }

  @PatchMapping("/ingredients/{id}")
  @PreAuthorize(MANAGEMENT)
  public IngredientResponse updateIngredient(@PathVariable String id, @Valid @RequestBody UpdateIngredientRequest request) {
    return stockService.updateIngredient(id, request);
  }

  @GetMapping("/ingredients/levels")
  public List<IngredientStockLevelResponse> ingredientLevels(@RequestParam String locationId) {
    return stockService.ingredientLevels(locationId);
  }

  @PostMapping("/ingredients/{id}/adjust")
  @PreAuthorize(MANAGEMENT)
  public StockAdjustmentResponse adjustIngredientStock(@PathVariable String id, @Valid @RequestBody AdjustStockRequest request) {
    return stockService.adjustIngredientStock(id, request);
  }

  @GetMapping("/ingredients/{id}/adjustments")
  @PreAuthorize(MANAGEMENT)
  public List<StockAdjustmentResponse> ingredientAdjustmentHistory(@PathVariable String id, @RequestParam String locationId) {
    return stockService.ingredientAdjustmentHistory(id, locationId);
  }

  // ---- Productos (stock DIRECT + receta) ----

  @GetMapping("/products/levels")
  public List<ProductStockLevelResponse> productLevels(@RequestParam String locationId) {
    return stockService.productLevels(locationId);
  }

  @GetMapping("/products/{id}/recipe")
  @PreAuthorize(MANAGEMENT)
  public List<ProductRecipeItemResponse> productRecipe(@PathVariable String id) {
    return stockService.getProductRecipe(id);
  }

  @PatchMapping("/products/{id}/config")
  @PreAuthorize(MANAGEMENT)
  public ProductResponse updateProductStockConfig(
      @PathVariable String id, @Valid @RequestBody UpdateProductStockConfigRequest request) {
    return stockService.updateProductStockConfig(id, request);
  }

  @PostMapping("/products/{id}/adjust")
  @PreAuthorize(MANAGEMENT)
  public StockAdjustmentResponse adjustProductStock(@PathVariable String id, @Valid @RequestBody AdjustStockRequest request) {
    return stockService.adjustProductStock(id, request);
  }

  @GetMapping("/products/{id}/adjustments")
  @PreAuthorize(MANAGEMENT)
  public List<StockAdjustmentResponse> productAdjustmentHistory(@PathVariable String id, @RequestParam String locationId) {
    return stockService.productAdjustmentHistory(id, locationId);
  }
}
