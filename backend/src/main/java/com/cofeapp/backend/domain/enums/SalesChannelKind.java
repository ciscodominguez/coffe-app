package com.cofeapp.backend.domain.enums;

/**
 * De qué app es un canal de venta (V19). {@code OTHER} = cualquier otra app
 * que el café use (una app local, Mercado Libre, el WhatsApp del café…),
 * con el nombre que el café le ponga; las demás tienen nombre fijo.
 */
public enum SalesChannelKind {
  PEDIDOSYA("PedidosYa"),
  RAPPI("Rappi"),
  UBER_EATS("Uber Eats"),
  OTHER(null);

  private final String defaultName;

  SalesChannelKind(String defaultName) {
    this.defaultName = defaultName;
  }

  /** Nombre fijo de la app; nulo para {@code OTHER} (lo elige el café). */
  public String defaultName() {
    return defaultName;
  }
}
