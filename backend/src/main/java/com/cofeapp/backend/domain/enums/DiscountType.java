package com.cofeapp.backend.domain.enums;

/** Descuento en porcentaje ("10%") o en monto fijo ("$500"). Espejo del CHECK de `discounts.type` (V1). */
public enum DiscountType {
  PERCENTAGE,
  FIXED_AMOUNT
}
