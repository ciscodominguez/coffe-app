package com.cofeapp.backend.option;

import com.cofeapp.backend.common.exception.BadRequestException;
import com.cofeapp.backend.common.exception.ConflictException;
import com.cofeapp.backend.common.exception.NotFoundException;
import com.cofeapp.backend.domain.Ingredient;
import com.cofeapp.backend.domain.OptionGroup;
import com.cofeapp.backend.domain.OptionItem;
import com.cofeapp.backend.domain.Product;
import com.cofeapp.backend.domain.ProductOptionGroup;
import com.cofeapp.backend.option.dto.OptionGroupResponse;
import com.cofeapp.backend.option.dto.OptionItemInput;
import com.cofeapp.backend.option.dto.OptionItemResponse;
import com.cofeapp.backend.option.dto.SaveOptionGroupRequest;
import com.cofeapp.backend.product.ProductRepository;
import com.cofeapp.backend.stock.IngredientRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grupos de opciones reutilizables (V11): "Leche", "Tamaño", "Extras"…
 * Se cargan una vez y se asignan a los productos que los usan — desde el
 * grupo (lista de productos) o desde el producto ({@link #setProductGroups}).
 *
 * <p>Qué se valida al guardar, para que al tomar un pedido nunca aparezca
 * un grupo imposible de completar:
 *
 * <ul>
 *   <li>El máximo no puede ser menor que el mínimo, ni el mínimo mayor que
 *       la cantidad de opciones.
 *   <li>No más opciones "marcadas de entrada" que el máximo.
 *   <li>Nombres de opción sin repetir dentro del grupo.
 *   <li>Insumo y cantidad van juntos (los dos o ninguno), y el insumo tiene
 *       que ser del negocio.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class OptionGroupService {

  private final OptionGroupRepository groupRepository;
  private final OptionItemRepository itemRepository;
  private final ProductOptionGroupRepository productGroupRepository;
  private final ProductRepository productRepository;
  private final IngredientRepository ingredientRepository;

  public List<OptionGroupResponse> findAll() {
    return toResponses(groupRepository.findAllByOrderByPositionAscCreatedAtAsc());
  }

  public OptionGroupResponse findOne(String id) {
    return toResponses(List.of(findGroupOrThrow(id))).get(0);
  }

  @Transactional
  public OptionGroupResponse create(SaveOptionGroupRequest request) {
    String name = request.getName().trim();
    assertNameFree(name, null);
    validateRules(request);

    OptionGroup group = new OptionGroup();
    applyRules(group, name, request);
    group.setPosition(groupRepository.findMaxPosition() + 1); // los nuevos van al final
    group = groupRepository.save(group);

    replaceOptions(group, request.getOptions());
    if (request.getProductIds() != null) {
      replaceGroupProducts(group.getId(), request.getProductIds());
    }
    return findOne(group.getId());
  }

  @Transactional
  public OptionGroupResponse update(String id, SaveOptionGroupRequest request) {
    OptionGroup group = findGroupOrThrow(id);
    String name = request.getName().trim();
    assertNameFree(name, group.getId());
    validateRules(request);

    applyRules(group, name, request);
    groupRepository.save(group);

    replaceOptions(group, request.getOptions());
    if (request.getProductIds() != null) {
      replaceGroupProducts(group.getId(), request.getProductIds());
    }
    return findOne(group.getId());
  }

  /**
   * Borrado físico: se van las opciones y las asignaciones a productos. Los
   * pedidos ya hechos no cambian (guardan su copia de lo elegido).
   */
  @Transactional
  public void delete(String id) {
    OptionGroup group = findGroupOrThrow(id);
    productGroupRepository.deleteAll(productGroupRepository.findAllByGroupId(group.getId()));
    itemRepository.deleteAll(itemRepository.findAllByGroupIdOrderByPositionAsc(group.getId()));
    groupRepository.delete(group);
  }

  /**
   * Cambia el orden en que se preguntan los grupos al tomar un pedido.
   * {@code groupIds} tiene que traer TODOS los grupos del negocio, en el
   * orden nuevo.
   */
  @Transactional
  public List<OptionGroupResponse> reorder(List<String> groupIds) {
    List<OptionGroup> groups = groupRepository.findAllByOrderByPositionAscCreatedAtAsc();
    Set<String> requested = new LinkedHashSet<>(groupIds);
    Set<String> existing = groups.stream().map(OptionGroup::getId).collect(Collectors.toSet());
    if (requested.size() != groupIds.size() || !requested.equals(existing)) {
      throw new BadRequestException("La lista para ordenar tiene que traer todos los grupos, sin repetir.");
    }
    Map<String, OptionGroup> byId = groups.stream().collect(Collectors.toMap(OptionGroup::getId, g -> g));
    int position = 0;
    for (String id : requested) {
      byId.get(id).setPosition(position++);
    }
    groupRepository.saveAll(groups);
    return findAll();
  }

  /** Desde el formulario del producto: qué grupos usa (reemplaza la lista). Devuelve los ids que quedaron. */
  @Transactional
  public List<String> setProductGroups(String productId, List<String> groupIds) {
    Product product =
        productRepository
            .findByIdScoped(productId)
            .orElseThrow(() -> new NotFoundException("No se encontró el producto " + productId + "."));
    Set<String> wanted = new LinkedHashSet<>(groupIds);
    if (!wanted.isEmpty() && groupRepository.findAllByIdInScoped(wanted).size() != wanted.size()) {
      throw new NotFoundException("Alguno de los grupos de opciones no existe.");
    }

    List<ProductOptionGroup> current = productGroupRepository.findAllByProductId(product.getId());
    Set<String> currentIds = current.stream().map(ProductOptionGroup::getGroupId).collect(Collectors.toSet());
    productGroupRepository.deleteAll(current.stream().filter(link -> !wanted.contains(link.getGroupId())).toList());
    productGroupRepository.flush();
    List<ProductOptionGroup> toAdd = new ArrayList<>();
    for (String groupId : wanted) {
      if (!currentIds.contains(groupId)) {
        toAdd.add(link(product.getId(), groupId));
      }
    }
    productGroupRepository.saveAll(toAdd);
    return List.copyOf(wanted);
  }

  // ---------------------------------------------------------------------

  private void validateRules(SaveOptionGroupRequest request) {
    int min = request.getMinSelect();
    Integer max = request.getMaxSelect();
    List<OptionItemInput> options = request.getOptions();

    if (max != null && max < min) {
      throw new BadRequestException("El máximo de opciones a elegir no puede ser menor que el mínimo.");
    }
    if (min > options.size()) {
      throw new BadRequestException(
          "El grupo pide elegir al menos " + min + " opciones, pero tiene " + options.size() + ".");
    }

    Set<String> names = new HashSet<>();
    long defaults = 0;
    for (OptionItemInput option : options) {
      String key = option.getName().trim().toLowerCase(Locale.ROOT);
      if (!names.add(key)) {
        throw new BadRequestException("La opción \"" + option.getName().trim() + "\" está repetida.");
      }
      boolean hasIngredient = option.getIngredientId() != null && !option.getIngredientId().isBlank();
      if (hasIngredient != (option.getIngredientQuantity() != null)) {
        throw new BadRequestException(
            "En \"" + option.getName().trim() + "\": si gasta un insumo, poné cuánto (y si no, dejá los dos vacíos).");
      }
      if (Boolean.TRUE.equals(option.getIsDefault())) defaults++;
    }
    if (max != null && defaults > max) {
      throw new BadRequestException(
          max == 1
              ? "Solo una opción puede venir marcada de entrada."
              : "No puede haber más opciones marcadas de entrada (" + defaults + ") que el máximo (" + max + ").");
    }
  }

  private void applyRules(OptionGroup group, String name, SaveOptionGroupRequest request) {
    group.setName(name);
    group.setMinSelect(request.getMinSelect());
    group.setMaxSelect(request.getMaxSelect());
  }

  /** Edita las opciones que vienen con id, crea las nuevas y borra las que ya no están. */
  private void replaceOptions(OptionGroup group, List<OptionItemInput> inputs) {
    Map<String, OptionItem> existing =
        itemRepository.findAllByGroupIdOrderByPositionAsc(group.getId()).stream()
            .collect(Collectors.toMap(OptionItem::getId, item -> item));

    Set<String> ingredientIds =
        inputs.stream()
            .map(OptionItemInput::getIngredientId)
            .filter(idValue -> idValue != null && !idValue.isBlank())
            .collect(Collectors.toSet());
    for (String ingredientId : ingredientIds) {
      ingredientRepository
          .findByIdScoped(ingredientId)
          .orElseThrow(() -> new NotFoundException("No se encontró el insumo " + ingredientId + "."));
    }

    Set<String> kept = new HashSet<>();
    List<OptionItem> toSave = new ArrayList<>();
    int position = 0;
    for (OptionItemInput input : inputs) {
      OptionItem item;
      if (input.getId() != null && !input.getId().isBlank()) {
        item = existing.get(input.getId());
        if (item == null) {
          throw new NotFoundException("La opción \"" + input.getName().trim() + "\" no es de este grupo.");
        }
        kept.add(item.getId());
      } else {
        item = new OptionItem();
        item.setGroupId(group.getId());
      }
      boolean hasIngredient = input.getIngredientId() != null && !input.getIngredientId().isBlank();
      item.setName(input.getName().trim());
      item.setPriceDelta(input.getPriceDelta());
      item.setIngredientId(hasIngredient ? input.getIngredientId() : null);
      item.setIngredientQuantity(hasIngredient ? input.getIngredientQuantity() : null);
      item.setIsDefault(Boolean.TRUE.equals(input.getIsDefault()));
      item.setIsAvailable(input.getIsAvailable() == null || input.getIsAvailable());
      item.setPosition(position++);
      toSave.add(item);
    }

    itemRepository.deleteAll(existing.values().stream().filter(item -> !kept.contains(item.getId())).toList());
    itemRepository.flush();
    itemRepository.saveAll(toSave);
  }

  private void replaceGroupProducts(String groupId, List<String> productIds) {
    Set<String> wanted = new LinkedHashSet<>(productIds);
    if (!wanted.isEmpty() && productRepository.findAllByIdInScoped(wanted).size() != wanted.size()) {
      throw new NotFoundException("Alguno de los productos elegidos no existe.");
    }
    List<ProductOptionGroup> current = productGroupRepository.findAllByGroupId(groupId);
    Set<String> currentIds = current.stream().map(ProductOptionGroup::getProductId).collect(Collectors.toSet());
    productGroupRepository.deleteAll(current.stream().filter(link -> !wanted.contains(link.getProductId())).toList());
    productGroupRepository.flush();
    List<ProductOptionGroup> toAdd = new ArrayList<>();
    for (String productId : wanted) {
      if (!currentIds.contains(productId)) {
        toAdd.add(link(productId, groupId));
      }
    }
    productGroupRepository.saveAll(toAdd);
  }

  private static ProductOptionGroup link(String productId, String groupId) {
    ProductOptionGroup link = new ProductOptionGroup();
    link.setProductId(productId);
    link.setGroupId(groupId);
    return link;
  }

  private void assertNameFree(String name, String ownId) {
    boolean taken =
        groupRepository.findAllByNameIgnoreCaseScoped(name).stream().anyMatch(g -> !Objects.equals(g.getId(), ownId));
    if (taken) {
      throw new ConflictException("Ya existe un grupo de opciones llamado \"" + name + "\".");
    }
  }

  private List<OptionGroupResponse> toResponses(List<OptionGroup> groups) {
    if (groups.isEmpty()) return List.of();
    List<String> groupIds = groups.stream().map(OptionGroup::getId).toList();

    Map<String, List<OptionItem>> itemsByGroup =
        itemRepository.findAllByGroupIdInOrderByPositionAsc(groupIds).stream()
            .collect(Collectors.groupingBy(OptionItem::getGroupId));
    Map<String, List<String>> productsByGroup =
        productGroupRepository.findAllByGroupIdIn(groupIds).stream()
            .collect(
                Collectors.groupingBy(
                    ProductOptionGroup::getGroupId,
                    Collectors.mapping(ProductOptionGroup::getProductId, Collectors.toList())));

    Set<String> ingredientIds =
        itemsByGroup.values().stream()
            .flatMap(List::stream)
            .map(OptionItem::getIngredientId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<String, Ingredient> ingredients = new HashMap<>();
    if (!ingredientIds.isEmpty()) {
      ingredientRepository.findAllById(ingredientIds).forEach(i -> ingredients.put(i.getId(), i));
    }

    return groups.stream()
        .map(
            group ->
                OptionGroupResponse.of(
                    group,
                    itemsByGroup.getOrDefault(group.getId(), List.of()).stream()
                        .map(item -> OptionItemResponse.of(item, ingredients.get(item.getIngredientId())))
                        .toList(),
                    productsByGroup.getOrDefault(group.getId(), List.of())))
        .toList();
  }

  private OptionGroup findGroupOrThrow(String id) {
    return groupRepository
        .findByIdScoped(id)
        .orElseThrow(() -> new NotFoundException("No se encontró el grupo de opciones " + id + "."));
  }
}
