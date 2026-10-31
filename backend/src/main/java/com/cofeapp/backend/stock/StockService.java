package com.cofeapp.backend.stock;

import com.cofeapp.backend.common.context.TenantContext;
import com.cofeapp.backend.common.exception.NotFoundException;
import com.cofeapp.backend.domain.Ingredient;
import com.cofeapp.backend.domain.IngredientStock;
import com.cofeapp.backend.domain.IngredientStockAdjustment;
import com.cofeapp.backend.domain.OrderItem;
import com.cofeapp.backend.domain.Product;
import com.cofeapp.backend.domain.ProductRecipeItem;
import com.cofeapp.backend.domain.ProductStock;
import com.cofeapp.backend.domain.ProductStockAdjustment;
import com.cofeapp.backend.domain.User;
import com.cofeapp.backend.domain.enums.StockMode;
import com.cofeapp.backend.location.LocationRepository;
import com.cofeapp.backend.product.ProductRepository;
import com.cofeapp.backend.product.dto.ProductResponse;
import com.cofeapp.backend.stock.dto.AdjustStockRequest;
import com.cofeapp.backend.stock.dto.CreateIngredientRequest;
import com.cofeapp.backend.stock.dto.IngredientResponse;
import com.cofeapp.backend.stock.dto.IngredientStockLevelResponse;
import com.cofeapp.backend.stock.dto.ProductRecipeItemResponse;
import com.cofeapp.backend.stock.dto.ProductStockLevelResponse;
import com.cofeapp.backend.stock.dto.RecipeItemInput;
import com.cofeapp.backend.stock.dto.StockAdjustmentResponse;
import com.cofeapp.backend.stock.dto.UpdateIngredientRequest;
import com.cofeapp.backend.stock.dto.UpdateProductStockConfigRequest;
import com.cofeapp.backend.user.UserRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock — Bloque D (ítems 12/13). Cuatro responsabilidades:
 *
 * <ol>
 *   <li>CRUD de insumos ({@link #createIngredient}/{@link
 *       #updateIngredient}) — el catálogo de insumos del negocio.
 *   <li>Configuración de stock de un producto ({@link
 *       #updateProductStockConfig}) — elige {@code StockMode} y, en modo
 *       {@code RECIPE}, la receta completa (ver {@link #replaceRecipe}).
 *   <li>Consulta de niveles por local ({@link #ingredientLevels}/{@link
 *       #productLevels}) — abierta a TODOS los roles (ver {@code
 *       StockController}), con la alerta de "stock bajo" ya calculada.
 *   <li>Movimiento de stock: automático al vender/cancelar un pedido
 *       ({@link #consumeForOrder}/{@link #restoreForOrder}, llamado desde
 *       {@code OrderService}) y manual, con auditoría ({@link
 *       #adjustIngredientStock}/{@link #adjustProductStock}).
 * </ol>
 *
 * <p>Stock NEGATIVO permitido a propósito en todo el servicio — Fran pidió
 * explícitamente no trabar una venta por falta de stock (ver el Javadoc de
 * {@code IngredientStock}). Ver ese mismo Javadoc sobre la simplificación
 * de concurrencia aceptada (sin lock pesimista en los incrementos).
 */
@Service
@RequiredArgsConstructor
public class StockService {

  private final IngredientRepository ingredientRepository;
  private final IngredientStockRepository ingredientStockRepository;
  private final IngredientStockAdjustmentRepository ingredientStockAdjustmentRepository;
  private final ProductRepository productRepository;
  private final ProductStockRepository productStockRepository;
  private final ProductRecipeItemRepository productRecipeItemRepository;
  private final ProductStockAdjustmentRepository productStockAdjustmentRepository;
  private final LocationRepository locationRepository;
  private final UserRepository userRepository;
  private final TenantContext tenantContext;

  /**
   * Una línea a descontar/reponer: el producto vendido/cancelado, la
   * cantidad de unidades del pedido y los insumos que suman las opciones
   * elegidas (V11 — ej. "Almendra: 150 ml de leche de almendra" por unidad).
   * Los insumos de las opciones se descuentan siempre, tenga o no el
   * producto control de stock propio.
   */
  public record StockConsumptionLine(Product product, int quantity, List<IngredientUse> optionIngredients) {
    public StockConsumptionLine(Product product, int quantity) {
      this(product, quantity, List.of());
    }
  }

  /** Cuánto de un insumo gasta UNA unidad (lo que suma una opción elegida). */
  public record IngredientUse(String ingredientId, BigDecimal quantityPerUnit) {}

  // ---------------------------------------------------------------------
  // Insumos (catálogo)
  // ---------------------------------------------------------------------

  public List<IngredientResponse> findAllIngredients() {
    return ingredientRepository.findAllByOrderByNameAsc().stream().map(IngredientResponse::from).toList();
  }

  @Transactional
  public IngredientResponse createIngredient(CreateIngredientRequest request) {
    Ingredient ingredient = new Ingredient();
    ingredient.setName(request.getName());
    ingredient.setUnit(request.getUnit());
    ingredient.setMinStock(request.getMinStock());
    return IngredientResponse.from(ingredientRepository.save(ingredient));
  }

  @Transactional
  public IngredientResponse updateIngredient(String id, UpdateIngredientRequest request) {
    Ingredient ingredient = findIngredientOrThrow(id);
    if (request.getName() != null) ingredient.setName(request.getName());
    if (request.getUnit() != null) ingredient.setUnit(request.getUnit());
    if (request.getMinStock() != null) ingredient.setMinStock(request.getMinStock());
    if (request.getIsActive() != null) ingredient.setIsActive(request.getIsActive());
    return IngredientResponse.from(ingredientRepository.save(ingredient));
  }

  // ---------------------------------------------------------------------
  // Configuración de stock de un producto (modo + receta)
  // ---------------------------------------------------------------------

  @Transactional
  public ProductResponse updateProductStockConfig(String productId, UpdateProductStockConfigRequest request) {
    Product product = findProductOrThrow(productId);

    product.setStockMode(request.getStockMode());
    product.setMinStock(request.getStockMode() == StockMode.DIRECT ? request.getMinStock() : null);
    product = productRepository.save(product);

    if (request.getStockMode() == StockMode.RECIPE) {
      replaceRecipe(product.getId(), request.getRecipeItems() == null ? List.of() : request.getRecipeItems());
    } else {
      // Cambiar a NONE/DIRECT deja la receta vieja sin uso — se borra para
      // que no quede "fantasma" si el producto vuelve a RECIPE más adelante.
      productRecipeItemRepository.deleteAllByProductId(product.getId());
    }

    return ProductResponse.from(product);
  }

  public List<ProductRecipeItemResponse> getProductRecipe(String productId) {
    findProductOrThrow(productId); // valida que el producto exista (y sea del negocio actual).
    List<ProductRecipeItem> items = productRecipeItemRepository.findAllByProductId(productId);
    if (items.isEmpty()) return List.of();

    Map<String, Ingredient> ingredientsById = ingredientRepository.findAllById(
            items.stream().map(ProductRecipeItem::getIngredientId).toList())
        .stream()
        .collect(Collectors.toMap(Ingredient::getId, i -> i));
    return items.stream()
        .filter(item -> ingredientsById.containsKey(item.getIngredientId()))
        .map(item -> ProductRecipeItemResponse.of(item, ingredientsById.get(item.getIngredientId())))
        .toList();
  }

  /** Reemplaza la receta completa de un producto — ver el Javadoc de {@code ProductRecipeItem} sobre por qué "reemplazar todo" en vez de un PATCH incremental. */
  private void replaceRecipe(String productId, List<RecipeItemInput> recipeItems) {
    for (RecipeItemInput input : recipeItems) {
      findIngredientOrThrow(input.getIngredientId()); // valida que cada insumo exista (y sea del negocio actual) antes de tocar nada.
    }
    productRecipeItemRepository.deleteAllByProductId(productId);
    List<ProductRecipeItem> lines =
        recipeItems.stream()
            .map(
                input -> {
                  ProductRecipeItem item = new ProductRecipeItem();
                  item.setProductId(productId);
                  item.setIngredientId(input.getIngredientId());
                  item.setQuantityPerUnit(input.getQuantityPerUnit());
                  return item;
                })
            .toList();
    productRecipeItemRepository.saveAll(lines);
  }

  // ---------------------------------------------------------------------
  // Consulta de niveles por local — abierta a todos los roles
  // ---------------------------------------------------------------------

  public List<IngredientStockLevelResponse> ingredientLevels(String locationId) {
    assertLocationExists(locationId);
    List<Ingredient> ingredients = ingredientRepository.findAllByIsActiveTrueOrderByNameAsc();
    Map<String, BigDecimal> currentByIngredient =
        ingredientStockRepository.findAllByLocationId(locationId).stream()
            .collect(Collectors.toMap(IngredientStock::getIngredientId, IngredientStock::getCurrentStock));
    return ingredients.stream()
        .map(ingredient -> IngredientStockLevelResponse.of(ingredient, currentByIngredient.getOrDefault(ingredient.getId(), BigDecimal.ZERO)))
        .toList();
  }

  public List<ProductStockLevelResponse> productLevels(String locationId) {
    assertLocationExists(locationId);
    List<Product> products =
        productRepository.findAllByOrderByNameAsc().stream()
            .filter(p -> Boolean.TRUE.equals(p.getIsActive()) && p.getStockMode() == StockMode.DIRECT)
            .toList();
    Map<String, BigDecimal> currentByProduct =
        productStockRepository.findAllByLocationId(locationId).stream()
            .collect(Collectors.toMap(ProductStock::getProductId, ProductStock::getCurrentStock));
    return products.stream()
        .map(product -> ProductStockLevelResponse.of(product, currentByProduct.getOrDefault(product.getId(), BigDecimal.ZERO)))
        .toList();
  }

  // ---------------------------------------------------------------------
  // Ajustes manuales — con auditoría, mismo patrón que CashMovement
  // ---------------------------------------------------------------------

  @Transactional
  public StockAdjustmentResponse adjustIngredientStock(String ingredientId, AdjustStockRequest request) {
    findIngredientOrThrow(ingredientId);
    assertLocationExists(request.getLocationId());

    IngredientStock stock = lockedIngredientStock(ingredientId, request.getLocationId());
    BigDecimal previous = stock.getCurrentStock();
    BigDecimal delta = request.getNewQuantity().subtract(previous);
    stock.setCurrentStock(request.getNewQuantity());
    ingredientStockRepository.save(stock);

    IngredientStockAdjustment adjustment = new IngredientStockAdjustment();
    adjustment.setIngredientId(ingredientId);
    adjustment.setLocationId(request.getLocationId());
    adjustment.setPreviousQuantity(previous);
    adjustment.setNewQuantity(request.getNewQuantity());
    adjustment.setDelta(delta);
    adjustment.setReason(request.getReason());
    adjustment.setCreatedById(tenantContext.getUserId());
    adjustment = ingredientStockAdjustmentRepository.save(adjustment);

    return StockAdjustmentResponse.from(adjustment, resolveName(adjustment.getCreatedById()));
  }

  @Transactional
  public StockAdjustmentResponse adjustProductStock(String productId, AdjustStockRequest request) {
    findProductOrThrow(productId);
    assertLocationExists(request.getLocationId());

    ProductStock stock = lockedProductStock(productId, request.getLocationId());
    BigDecimal previous = stock.getCurrentStock();
    BigDecimal delta = request.getNewQuantity().subtract(previous);
    stock.setCurrentStock(request.getNewQuantity());
    productStockRepository.save(stock);

    ProductStockAdjustment adjustment = new ProductStockAdjustment();
    adjustment.setProductId(productId);
    adjustment.setLocationId(request.getLocationId());
    adjustment.setPreviousQuantity(previous);
    adjustment.setNewQuantity(request.getNewQuantity());
    adjustment.setDelta(delta);
    adjustment.setReason(request.getReason());
    adjustment.setCreatedById(tenantContext.getUserId());
    adjustment = productStockAdjustmentRepository.save(adjustment);

    return StockAdjustmentResponse.from(adjustment, resolveName(adjustment.getCreatedById()));
  }

  public List<StockAdjustmentResponse> ingredientAdjustmentHistory(String ingredientId, String locationId) {
    List<IngredientStockAdjustment> adjustments =
        ingredientStockAdjustmentRepository.findAllByIngredientIdAndLocationIdOrderByCreatedAtDesc(ingredientId, locationId);
    Map<String, String> names = resolveNames(adjustments.stream().map(IngredientStockAdjustment::getCreatedById).collect(Collectors.toSet()));
    return adjustments.stream().map(a -> StockAdjustmentResponse.from(a, names.get(a.getCreatedById()))).toList();
  }

  public List<StockAdjustmentResponse> productAdjustmentHistory(String productId, String locationId) {
    List<ProductStockAdjustment> adjustments =
        productStockAdjustmentRepository.findAllByProductIdAndLocationIdOrderByCreatedAtDesc(productId, locationId);
    Map<String, String> names = resolveNames(adjustments.stream().map(ProductStockAdjustment::getCreatedById).collect(Collectors.toSet()));
    return adjustments.stream().map(a -> StockAdjustmentResponse.from(a, names.get(a.getCreatedById()))).toList();
  }

  // ---------------------------------------------------------------------
  // Descuento automático al vender / reposición al cancelar — llamado desde OrderService
  // ---------------------------------------------------------------------

  /** Al CREAR un pedido (no al entregarlo) — ver el Javadoc de {@code OrderService#create}. */
  @Transactional
  public void consumeForOrder(List<StockConsumptionLine> lines, String locationId) {
    applyStockDelta(lines, locationId, -1);
  }

  /**
   * Al CANCELAR un pedido — repone lo que {@link #consumeForOrder} había
   * descontado. Los insumos de las opciones ({@code optionIngredientsByItemId},
   * por id de ítem) salen de la copia guardada al vender, así se repone
   * exactamente lo mismo aunque la opción se haya editado después.
   */
  @Transactional
  public void restoreForOrder(
      List<OrderItem> items, Map<String, List<IngredientUse>> optionIngredientsByItemId, String locationId) {
    if (items.isEmpty()) return;
    List<String> productIds = items.stream().map(OrderItem::getProductId).distinct().toList();
    Map<String, Product> productsById =
        productRepository.findAllByIdInScoped(productIds).stream().collect(Collectors.toMap(Product::getId, p -> p));
    List<StockConsumptionLine> lines =
        items.stream()
            .filter(item -> productsById.containsKey(item.getProductId()))
            .map(
                item ->
                    new StockConsumptionLine(
                        productsById.get(item.getProductId()),
                        item.getQuantity(),
                        optionIngredientsByItemId.getOrDefault(item.getId(), List.of())))
            .toList();
    applyStockDelta(lines, locationId, 1);
  }

  /**
   * {@code sign = -1} descuenta (venta), {@code sign = +1} repone (cancelación) — mismo cálculo, signo opuesto.
   *
   * <p>Prueba de carga (oct. 2026): con 20 ventas a la vez del mismo producto
   * se perdían descuentos (cada venta leía el mismo número y lo pisaba). Ahora
   * se juntan las cantidades por producto/insumo y cada fila se bloquea
   * mientras se actualiza, siempre en el mismo orden (por id) para que dos
   * ventas no se traben entre sí.
   */
  private void applyStockDelta(List<StockConsumptionLine> lines, String locationId, int sign) {
    java.util.TreeMap<String, BigDecimal> productDeltas = new java.util.TreeMap<>();
    java.util.TreeMap<String, BigDecimal> ingredientDeltas = new java.util.TreeMap<>();

    for (StockConsumptionLine line : lines) {
      if (line.product().getStockMode() == StockMode.DIRECT) {
        productDeltas.merge(line.product().getId(), BigDecimal.valueOf((long) sign * line.quantity()), BigDecimal::add);
      }
      // V11 — insumos de las opciones elegidas (independiente del modo del producto).
      if (line.optionIngredients() != null) {
        for (IngredientUse use : line.optionIngredients()) {
          if (use.ingredientId() == null || use.quantityPerUnit() == null) continue;
          BigDecimal consumed = use.quantityPerUnit().multiply(BigDecimal.valueOf(line.quantity()));
          ingredientDeltas.merge(use.ingredientId(), consumed.multiply(BigDecimal.valueOf(sign)), BigDecimal::add);
        }
      }
    }

    List<StockConsumptionLine> recipeLines =
        lines.stream().filter(l -> l.product().getStockMode() == StockMode.RECIPE).toList();
    if (!recipeLines.isEmpty()) {
      List<String> productIds = recipeLines.stream().map(l -> l.product().getId()).toList();
      Map<String, List<ProductRecipeItem>> recipeByProduct =
          productRecipeItemRepository.findAllByProductIdIn(productIds).stream()
              .collect(Collectors.groupingBy(ProductRecipeItem::getProductId));
      for (StockConsumptionLine line : recipeLines) {
        for (ProductRecipeItem recipeItem : recipeByProduct.getOrDefault(line.product().getId(), List.of())) {
          BigDecimal consumed = recipeItem.getQuantityPerUnit().multiply(BigDecimal.valueOf(line.quantity()));
          ingredientDeltas.merge(recipeItem.getIngredientId(), consumed.multiply(BigDecimal.valueOf(sign)), BigDecimal::add);
        }
      }
    }

    productDeltas.forEach(
        (productId, delta) -> {
          ProductStock stock = lockedProductStock(productId, locationId);
          stock.setCurrentStock(stock.getCurrentStock().add(delta));
          productStockRepository.save(stock);
        });
    ingredientDeltas.forEach(
        (ingredientId, delta) -> {
          IngredientStock stock = lockedIngredientStock(ingredientId, locationId);
          stock.setCurrentStock(stock.getCurrentStock().add(delta));
          ingredientStockRepository.save(stock);
        });
  }

  /** La fila de stock del producto en el local, creada si hacía falta y bloqueada hasta terminar. */
  /*
   * Primero se bloquea la fila si ya existe (lo normal). Solo si no existe se
   * crea con INSERT IGNORE y se vuelve a bloquear: hacer el INSERT IGNORE
   * siempre, aunque la fila exista, deja un bloqueo compartido que con
   * varias ventas a la vez terminaba en "deadlock".
   */
  private ProductStock lockedProductStock(String productId, String locationId) {
    return productStockRepository
        .lockByProductIdAndLocationId(productId, locationId)
        .orElseGet(
            () -> {
              productStockRepository.insertIfAbsent(tenantContext.getBusinessId(), productId, locationId);
              return productStockRepository
                  .lockByProductIdAndLocationId(productId, locationId)
                  .orElseThrow(() -> new IllegalStateException("No se pudo leer el stock del producto " + productId));
            });
  }

  private IngredientStock lockedIngredientStock(String ingredientId, String locationId) {
    return ingredientStockRepository
        .lockByIngredientIdAndLocationId(ingredientId, locationId)
        .orElseGet(
            () -> {
              ingredientStockRepository.insertIfAbsent(tenantContext.getBusinessId(), ingredientId, locationId);
              return ingredientStockRepository
                  .lockByIngredientIdAndLocationId(ingredientId, locationId)
                  .orElseThrow(() -> new IllegalStateException("No se pudo leer el stock del insumo " + ingredientId));
            });
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  private Map<String, String> resolveNames(Set<String> userIds) {
    Map<String, String> names = new HashMap<>();
    if (userIds.isEmpty()) return names;
    for (User user : userRepository.findAllByIdInScoped(userIds)) {
      names.put(user.getId(), user.getFirstName() + " " + user.getLastName());
    }
    return names;
  }

  private String resolveName(String userId) {
    return resolveNames(Set.of(userId)).get(userId);
  }

  private void assertLocationExists(String locationId) {
    locationRepository
        .findByIdScoped(locationId)
        .orElseThrow(() -> new NotFoundException("No se encontró el local " + locationId + "."));
  }

  private Ingredient findIngredientOrThrow(String id) {
    return ingredientRepository
        .findByIdScoped(id)
        .orElseThrow(() -> new NotFoundException("No se encontró el insumo " + id + "."));
  }

  private Product findProductOrThrow(String id) {
    return productRepository
        .findByIdScoped(id)
        .orElseThrow(() -> new NotFoundException("No se encontró el producto " + id + "."));
  }
}
