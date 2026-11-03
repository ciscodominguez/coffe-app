package com.cofeapp.backend.option.dto;

import com.cofeapp.backend.domain.OptionGroup;
import java.time.Instant;
import java.util.List;

/**
 * Un grupo con sus opciones y los productos que lo usan. La pantalla de
 * pedidos trae todos los grupos una vez y arma, por producto, qué preguntar.
 */
public record OptionGroupResponse(
    String id,
    String name,
    int minSelect,
    Integer maxSelect,
    List<OptionItemResponse> options,
    List<String> productIds,
    Instant createdAt,
    Instant updatedAt) {

  public static OptionGroupResponse of(OptionGroup group, List<OptionItemResponse> options, List<String> productIds) {
    return new OptionGroupResponse(
        group.getId(),
        group.getName(),
        group.getMinSelect() != null ? group.getMinSelect() : 0,
        group.getMaxSelect(),
        options,
        productIds,
        group.getCreatedAt(),
        group.getUpdatedAt());
  }
}
