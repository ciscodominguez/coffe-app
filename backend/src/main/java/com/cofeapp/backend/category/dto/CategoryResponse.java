package com.cofeapp.backend.category.dto;

import com.cofeapp.backend.domain.Category;
import java.time.Instant;

public record CategoryResponse(
    String id,
    String name,
    String description,
    int displayOrder,
    boolean isActive,
    Instant createdAt,
    Instant updatedAt) {

  public static CategoryResponse from(Category category) {
    return new CategoryResponse(
        category.getId(),
        category.getName(),
        category.getDescription(),
        category.getDisplayOrder(),
        Boolean.TRUE.equals(category.getIsActive()),
        category.getCreatedAt(),
        category.getUpdatedAt());
  }
}
