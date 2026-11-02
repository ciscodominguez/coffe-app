package com.cofeapp.backend.mercadopago.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConnectMercadoPagoRequest {

  @NotBlank(message = "Pegá el Access Token de tu cuenta de Mercado Pago.")
  @Size(max = 300, message = "Ese token es demasiado largo.")
  private String accessToken;
}
