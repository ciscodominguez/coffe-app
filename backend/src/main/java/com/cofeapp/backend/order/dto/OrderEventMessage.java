package com.cofeapp.backend.order.dto;

/**
 * Envoltorio que viaja por el tópico STOMP del local — STOMP no distingue
 * "tipos" de mensaje dentro de un mismo tópico, así que el campo
 * {@code event} cumple ese rol a mano.
 *
 * <p>Nombres de {@code event} en uso hoy: {@code order:created} (alta de
 * pedido), {@code order:status_changed} (cualquier transición de estado,
 * incluida la que cierra el pedido) y {@code order:updated} (cambios que no
 * son ni alta ni transición de estado — hoy, un cobro nuevo registrado con
 * {@code OrderService#addPayment}; es el nombre genérico para cambios,
 * así un descuento futuro puede reusarlo sin tocar este envoltorio).
 *
 * @param event nombre del evento (uno de los de arriba).
 * @param data el pedido completo (con sus items) — así el cliente nunca
 *     necesita un segundo pedido HTTP para
 *     tener el estado completo tras recibir el evento.
 */
public record OrderEventMessage(String event, OrderResponse data) {}
