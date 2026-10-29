package com.cofeapp.backend.auth.dto;

/** Forma de la respuesta de /auth/register-business y /auth/login. */
public record AuthResponse(String accessToken, AuthUserDto user) {}
