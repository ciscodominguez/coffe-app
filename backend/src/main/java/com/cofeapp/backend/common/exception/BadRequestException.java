package com.cofeapp.backend.common.exception;

/** Error 400 (pedido inválido). */
public class BadRequestException extends RuntimeException {
  public BadRequestException(String message) {
    super(message);
  }
}
