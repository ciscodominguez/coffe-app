package com.cofeapp.backend.dashboard.dto;

import java.math.BigDecimal;

/**
 * Una línea del ranking de productos más vendidos — Bloque E. {@code
 * productName} es el nombre SNAPSHOT que ya trae cada {@code OrderItem}
 * (nunca se vuelve a leer de {@code Product}), mismo criterio de
 * inmutabilidad al momento de la venta que el resto del proyecto — así el
 * ranking de un período viejo sigue mostrando el nombre que tenía el
 * producto en ese momento, aunque después se haya renombrado o dado de
 * baja. {@code quantity}/{@code revenue} son la suma de todos los {@code
 * OrderItem} de ese producto (agrupados por {@code productId}) dentro del
 * período consultado.
 */
public record TopProductResponse(String productId, String productName, long quantity, BigDecimal revenue) {}
