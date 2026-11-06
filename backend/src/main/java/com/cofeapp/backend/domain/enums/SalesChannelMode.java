package com.cofeapp.backend.domain.enums;

/**
 * Cómo trabaja un canal de venta (V19): apagado, carga manual o
 * automático. {@code AUTO} queda reservado para cuando COFFE_APP esté
 * aprobado como integrador por cada app (ver
 * docs/INTEGRACION-APPS-DELIVERY.md): por ahora no se puede elegir.
 */
public enum SalesChannelMode {
  OFF,
  MANUAL,
  AUTO,
}
