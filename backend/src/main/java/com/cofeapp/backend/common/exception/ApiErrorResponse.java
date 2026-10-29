package com.cofeapp.backend.common.exception;

/**
 * Forma del body de error: {@code { statusCode, message, error }}. Todo
 * error de la API respeta este mismo shape a propósito, porque el frontend
 * lee {@code message} para mostrarle el error al usuario.
 *
 * @param message string o lista de strings (errores de validación, uno por campo).
 */
public record ApiErrorResponse(int statusCode, Object message, String error) {}
