package com.cofeapp.backend.common.exception;

/** HTTP 429 — demasiados intentos (login, "olvidé mi contraseña"). */
public class TooManyRequestsException extends RuntimeException {
  public TooManyRequestsException(String message) {
    super(message);
  }
}
