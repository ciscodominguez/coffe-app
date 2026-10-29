package com.cofeapp.backend.user.dto;

import com.cofeapp.backend.domain.User;
import java.time.Instant;

/** Nunca incluye {@code passwordHash}. */
public record UserResponse(
    String id,
    String email,
    String firstName,
    String lastName,
    String role,
    boolean isActive,
    String locationId,
    Instant createdAt,
    Instant updatedAt) {

  public static UserResponse from(User user) {
    return new UserResponse(
        user.getId(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        user.getRole().name(),
        Boolean.TRUE.equals(user.getIsActive()),
        user.getLocationId(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }
}
