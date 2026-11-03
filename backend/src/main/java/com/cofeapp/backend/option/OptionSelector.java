package com.cofeapp.backend.option;

import com.cofeapp.backend.common.exception.BadRequestException;
import com.cofeapp.backend.domain.OptionGroup;
import com.cofeapp.backend.domain.OptionItem;
import com.cofeapp.backend.domain.OrderItemOption;
import com.cofeapp.backend.domain.Product;
import com.cofeapp.backend.domain.ProductOptionGroup;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Al tomar un pedido: valida las opciones elegidas para un producto y
 * devuelve la copia que se guarda con el ítem ({@link OrderItemOption}, sin
 * {@code orderItemId} todavía). Lo usa {@code OrderService#buildItem}.
 *
 * <p>Reglas:
 *
 * <ul>
 *   <li>Cada opción elegida tiene que ser de un grupo asignado al producto
 *       y estar disponible ("hay").
 *   <li>Por grupo, la cantidad elegida respeta el mínimo y el máximo. Si en
 *       un grupo obligatorio no queda ninguna opción disponible (se acabó
 *       todo), el grupo deja de ser obligatorio: no se traba la venta —
 *       mismo criterio que el stock negativo.
 *   <li>Nunca se confía en el precio que mande el cliente: el extra sale de
 *       la opción guardada.
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class OptionSelector {

  private final ProductOptionGroupRepository productGroupRepository;
  private final OptionGroupRepository groupRepository;
  private final OptionItemRepository itemRepository;

  public List<OrderItemOption> resolve(Product product, List<String> requestedOptionIds) {
    List<String> requested = requestedOptionIds == null ? List.of() : requestedOptionIds;
    Set<String> chosen = new LinkedHashSet<>(requested);
    if (chosen.size() != requested.size()) {
      throw new BadRequestException("En " + product.getName() + " hay una opción elegida dos veces.");
    }

    List<String> groupIds =
        productGroupRepository.findAllByProductId(product.getId()).stream().map(ProductOptionGroup::getGroupId).toList();
    if (groupIds.isEmpty()) {
      if (!chosen.isEmpty()) {
        throw new BadRequestException(product.getName() + " no tiene opciones para elegir.");
      }
      return List.of();
    }

    List<OptionGroup> groups = groupRepository.findAllByIdInScoped(groupIds);
    Map<String, List<OptionItem>> itemsByGroup =
        itemRepository.findAllByGroupIdInOrderByPositionAsc(groupIds).stream()
            .collect(Collectors.groupingBy(OptionItem::getGroupId));
    Map<String, OptionItem> itemsById = new HashMap<>();
    itemsByGroup.values().forEach(list -> list.forEach(item -> itemsById.put(item.getId(), item)));

    for (String optionId : chosen) {
      OptionItem item = itemsById.get(optionId);
      if (item == null) {
        throw new BadRequestException("Una de las opciones elegidas no corresponde a " + product.getName() + ".");
      }
      if (!Boolean.TRUE.equals(item.getIsAvailable())) {
        throw new BadRequestException("\"" + item.getName() + "\" no está disponible ahora.");
      }
    }

    List<OrderItemOption> result = new ArrayList<>();
    for (OptionGroup group : groups) {
      List<OptionItem> items = itemsByGroup.getOrDefault(group.getId(), List.of());
      long available = items.stream().filter(i -> Boolean.TRUE.equals(i.getIsAvailable())).count();
      List<OptionItem> picked = items.stream().filter(i -> chosen.contains(i.getId())).toList();

      int min = (int) Math.min(group.getMinSelect() != null ? group.getMinSelect() : 0, available);
      Integer max = group.getMaxSelect();
      if (picked.size() < min) {
        throw new BadRequestException(
            min == 1
                ? "Para " + product.getName() + " elegí una opción de \"" + group.getName() + "\"."
                : "Para " + product.getName() + " elegí al menos " + min + " opciones de \"" + group.getName() + "\".");
      }
      if (max != null && picked.size() > max) {
        throw new BadRequestException(
            max == 1
                ? "En \"" + group.getName() + "\" se puede elegir una sola opción."
                : "En \"" + group.getName() + "\" se pueden elegir hasta " + max + " opciones.");
      }

      for (OptionItem item : picked) {
        OrderItemOption copy = new OrderItemOption();
        copy.setOptionId(item.getId());
        copy.setGroupName(group.getName());
        copy.setOptionName(item.getName());
        copy.setPriceDelta(item.getPriceDelta() != null ? item.getPriceDelta() : BigDecimal.ZERO);
        copy.setIngredientId(item.getIngredientId());
        copy.setIngredientQuantity(item.getIngredientQuantity());
        result.add(copy);
      }
    }
    return result;
  }
}
