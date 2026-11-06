package com.cofeapp.backend.legal;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Desde dónde se hizo la request actual (IP y navegador), para dejar constancia. */
public final class ClientInfo {

  private ClientInfo() {}

  public static String ip() {
    HttpServletRequest request = current();
    if (request == null) return null;
    String forwarded = request.getHeader("X-Forwarded-For");
    String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    return truncate(ip, 64);
  }

  public static String userAgent() {
    HttpServletRequest request = current();
    return request == null ? null : truncate(request.getHeader("User-Agent"), 255);
  }

  private static HttpServletRequest current() {
    return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
        ? attributes.getRequest()
        : null;
  }

  private static String truncate(String value, int max) {
    return value == null || value.length() <= max ? value : value.substring(0, max);
  }
}
