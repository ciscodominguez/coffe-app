package com.cofeapp.backend.domain.enums;

/**
 * Condición fiscal del negocio frente a AFIP/ARCA. Se usa recién desde la
 * Etapa 4 (facturación electrónica) pero se modela ya en la Etapa 1 porque
 * es un dato de alta del negocio.
 */
public enum FiscalCondition {
  MONOTRIBUTO,
  RESPONSABLE_INSCRIPTO,
  EXENTO,
}
