package com.cofeapp.backend.mercadopago.client;

/**
 * Error al hablar con Mercado Pago. {@code httpStatus} es 0 si ni siquiera
 * se pudo conectar. El mensaje ya viene listo para mostrarle a la persona.
 */
public class MercadoPagoApiException extends RuntimeException {

  private final int httpStatus;

  public MercadoPagoApiException(int httpStatus, String message) {
    super(message);
    this.httpStatus = httpStatus;
  }

  public MercadoPagoApiException(int httpStatus, String message, Throwable cause) {
    super(message, cause);
    this.httpStatus = httpStatus;
  }

  public int getHttpStatus() {
    return httpStatus;
  }

  /** 401/403: el token no sirve (mal copiado, revocado, o sin permisos). */
  public boolean isAuthError() {
    return httpStatus == 401 || httpStatus == 403;
  }
}
