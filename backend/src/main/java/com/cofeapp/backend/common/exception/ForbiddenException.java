package com.cofeapp.backend.common.exception;

/** Error 403 (acción no permitida). */
public class ForbiddenException extends RuntimeException {
  public ForbiddenException(String message) {
    super(message);
  }
}
