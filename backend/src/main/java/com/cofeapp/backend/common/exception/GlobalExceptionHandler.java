package com.cofeapp.backend.common.exception;

import com.cofeapp.backend.mercadopago.client.MercadoPagoApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce cada excepción a una misma forma de respuesta —
 * {@code { statusCode, message, error }} (ver {@link ApiErrorResponse}) —
 * con el código HTTP que corresponde a cada una.
 *
 * <p>{@code MethodArgumentNotValidException} (Bean Validation, disparada
 * por {@code @Valid} en los controllers): junta un mensaje por cada campo
 * inválido en un array.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException ex) {
    return build(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException ex) {
    return build(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ApiErrorResponse> handleForbidden(ForbiddenException ex) {
    return build(HttpStatus.FORBIDDEN, ex.getMessage());
  }

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException ex) {
    return build(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(TooManyRequestsException.class)
  public ResponseEntity<ApiErrorResponse> handleTooManyRequests(TooManyRequestsException ex) {
    return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
  }

  /** Mercado Pago no respondió o respondió con error: 502, con un mensaje entendible (ver HttpMercadoPagoClient). */
  @ExceptionHandler(MercadoPagoApiException.class)
  public ResponseEntity<ApiErrorResponse> handleMercadoPago(MercadoPagoApiException ex) {
    return build(HttpStatus.BAD_GATEWAY, ex.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
    // Disparada por @PreAuthorize cuando el rol
    // del token no está entre los permitidos.
    return build(HttpStatus.FORBIDDEN, "No tenés permisos para acceder a este recurso.");
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex) {
    return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    List<String> messages =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .toList();
    ApiErrorResponse body =
        new ApiErrorResponse(HttpStatus.BAD_REQUEST.value(), messages, "Bad Request");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
    // Red de seguridad: cada service que puede violar un unique constraint
    // (email, taxId) ya la captura localmente con un mensaje específico
    // (ver UserService/AuthService) — esto solo cubre el caso de que algún
    // path nuevo se olvide de hacerlo.
    return build(HttpStatus.CONFLICT, "El registro entra en conflicto con datos ya existentes.");
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
    return build(HttpStatus.BAD_REQUEST, "El body de la request no es JSON válido.");
  }

  /**
   * Una dirección que el servidor no conoce. Pasa sobre todo si se
   * actualizó el frontend pero el backend sigue corriendo la versión
   * anterior: antes salía como "error interno" y confundía.
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
    log.warn("Dirección desconocida: {} {}", request.getMethod(), request.getRequestURI());
    return build(
        HttpStatus.NOT_FOUND,
        "El servidor no tiene esta función todavía. Si recién actualizaste la app, reiniciá el backend.");
  }

  // ---- Pedidos mal armados (prueba de seguridad, oct. 2026): antes salían como "error interno". ----

  @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
  public ResponseEntity<ApiErrorResponse> handleMissingParam(org.springframework.web.bind.MissingServletRequestParameterException ex) {
    return build(HttpStatus.BAD_REQUEST, "Falta el dato \"" + ex.getParameterName() + "\".");
  }

  @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiErrorResponse> handleTypeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
    return build(HttpStatus.BAD_REQUEST, "El dato \"" + ex.getName() + "\" tiene un formato inválido.");
  }

  @ExceptionHandler({
    org.springframework.web.multipart.support.MissingServletRequestPartException.class,
    org.springframework.web.multipart.MultipartException.class
  })
  public ResponseEntity<ApiErrorResponse> handleMissingFile(Exception ex) {
    return build(HttpStatus.BAD_REQUEST, "Falta el archivo.");
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ApiErrorResponse> handleMediaType(org.springframework.web.HttpMediaTypeNotSupportedException ex) {
    return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato de envío no soportado.");
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiErrorResponse> handleMethod(org.springframework.web.HttpRequestMethodNotSupportedException ex) {
    return build(HttpStatus.METHOD_NOT_ALLOWED, "Esa operación no existe para esta dirección.");
  }

  @ExceptionHandler(org.springframework.web.method.annotation.HandlerMethodValidationException.class)
  public ResponseEntity<ApiErrorResponse> handleParamValidation(org.springframework.web.method.annotation.HandlerMethodValidationException ex) {
    return build(HttpStatus.BAD_REQUEST, "Algún dato enviado no es válido.");
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiErrorResponse> handleIllegalState(
      IllegalStateException ex, HttpServletRequest request) {
    // Cubre, entre otros, TenantContext accedido fuera de una request
    // autenticada — nunca debería llegarle a un usuario real, así que se
    // loguea completo server-side y se responde genérico.
    log.error("Estado inválido procesando {} {}", request.getMethod(), request.getRequestURI(), ex);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error interno.");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error("Error no controlado procesando {} {}", request.getMethod(), request.getRequestURI(), ex);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error interno.");
  }

  private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message) {
    ApiErrorResponse body = new ApiErrorResponse(status.value(), message, status.getReasonPhrase());
    return ResponseEntity.status(status).body(body);
  }
}
