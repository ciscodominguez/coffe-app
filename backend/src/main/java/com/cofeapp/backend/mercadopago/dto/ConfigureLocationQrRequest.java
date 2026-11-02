package com.cofeapp.backend.mercadopago.dto;

import com.cofeapp.backend.domain.enums.QrMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Configurar el cobro con QR de un local. La dirección solo hace falta la
 * primera vez (Mercado Pago la pide para crear la sucursal); después se
 * puede mandar solo {@code qrMode} para cambiar de QR en pantalla a QR
 * impreso o al revés.
 */
@Getter
@Setter
public class ConfigureLocationQrRequest {

  private QrMode qrMode;

  @Size(max = 120)
  private String streetName;

  @Size(max = 20)
  private String streetNumber;

  @Size(max = 80)
  private String cityName;

  @Size(max = 80)
  private String stateName;

  @DecimalMin(value = "-90.0", message = "La latitud tiene que estar entre -90 y 90.")
  @DecimalMax(value = "90.0", message = "La latitud tiene que estar entre -90 y 90.")
  private Double latitude;

  @DecimalMin(value = "-180.0", message = "La longitud tiene que estar entre -180 y 180.")
  @DecimalMax(value = "180.0", message = "La longitud tiene que estar entre -180 y 180.")
  private Double longitude;

  @Size(max = 120)
  private String reference;
}
