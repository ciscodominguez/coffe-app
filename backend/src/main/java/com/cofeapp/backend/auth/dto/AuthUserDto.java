package com.cofeapp.backend.auth.dto;

/** Nunca incluye {@code passwordHash} ni ningún otro campo sensible. */
public record AuthUserDto(
    String id,
    String email,
    String firstName,
    String lastName,
    String role,
    String businessId,
    String locationId) {}
