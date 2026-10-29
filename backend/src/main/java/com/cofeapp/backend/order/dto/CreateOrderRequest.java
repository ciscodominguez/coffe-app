package com.cofeapp.backend.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code idempotencyKey} + {@code clientTimestamp} existen para la cola
 * offline del frontend de Meseras (prevista en el roadmap) — hoy,
 * sin esa cola todavía en el frontend Java/Angular, alcanza con que el
 * cliente genere un UUID random como key en cada intento real de envío.
 */
@Getter
@Setter
public class CreateOrderRequest {

  @NotBlank
  private String locationId;

  /** Nulo = pedido de mostrador/takeaway, sin mesa asociada. */
  private String tableId;

  @NotBlank
  @Size(max = 100)
  private String idempotencyKey;

  @NotNull
  private Instant clientTimestamp;

  @Size(max = 1000)
  private String notes;

  @NotEmpty
  @Valid
  private List<CreateOrderItemRequest> items;

  /** V19 — solo si el pedido vino por una app de delivery. */
  @Valid
  private ChannelOrderInput channel;
}
