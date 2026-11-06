package com.cofeapp.backend.channel.dto;

import com.cofeapp.backend.domain.enums.SalesChannelKind;
import com.cofeapp.backend.domain.enums.SalesChannelMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Prender, apagar o configurar una app en un local (V19). Al crear van
 * {@code locationId} y {@code kind}; {@code name} solo para "otra app"
 * (las demás tienen nombre fijo). Al editar, lo que no viene no cambia.
 */
public record SaveSalesChannelRequest(
    String locationId,
    SalesChannelKind kind,
    @Size(max = 40, message = "El nombre puede tener hasta 40 letras.") String name,
    SalesChannelMode mode,
    @DecimalMin(value = "0", message = "La comisión no puede ser negativa.")
        @DecimalMax(value = "100", message = "La comisión no puede pasar el 100%.")
        BigDecimal commissionPct) {}
