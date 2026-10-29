package com.cofeapp.backend.common.exception;

/** Error 404 (recurso no encontrado). */
public class NotFoundException extends RuntimeException {
  public NotFoundException(String message) {
    super(message);
  }
}
