package com.cofeapp.backend.domain.enums;

/**
 * Cómo paga el cliente con QR de Mercado Pago en un local (V10):
 *
 * <ul>
 *   <li>{@link #DYNAMIC}: se genera un QR nuevo por cada cobro y se muestra
 *       en la pantalla (tablet del mostrador o celular del mozo en la mesa).
 *   <li>{@link #STATIC}: un QR fijo impreso en el mostrador; al cobrar, el
 *       sistema le "carga" el monto y el cliente lo escanea. Mercado Pago
 *       permite un solo cobro a la vez por QR fijo.
 * </ul>
 */
public enum QrMode {
  DYNAMIC,
  STATIC,
}
