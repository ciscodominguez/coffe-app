package com.cofeapp.backend.common.exception;

/** Error 409 (conflicto con el estado actual, ej. un dato duplicado). */
public class ConflictException extends RuntimeException {
  public ConflictException(String message) {
    super(message);
  }
}
