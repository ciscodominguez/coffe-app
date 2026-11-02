package com.cofeapp.backend.domain.enums;

/** Estado de un cobro con QR de Mercado Pago — ver el Javadoc de {@link com.cofeapp.backend.domain.MpQrCharge}. */
public enum QrChargeStatus {
  /** Esperando que el cliente pague. */
  PENDING,
  /** Mercado Pago confirmó el pago y quedó registrado como cobro del pedido. */
  PAID,
  /**
   * Mercado Pago confirmó el pago, pero no se pudo registrar solo en el
   * pedido (por ejemplo, ya estaba cerrado). La plata entró: hay que
   * revisarlo y registrarlo a mano.
   */
  PAID_UNAPPLIED,
  CANCELLED,
  EXPIRED,
  FAILED,
}
