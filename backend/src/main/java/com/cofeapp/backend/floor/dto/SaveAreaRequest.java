package com.cofeapp.backend.floor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Crear (con {@code locationId}) o renombrar / reordenar una zona. */
public record SaveAreaRequest(
    String locationId,
    @NotBlank(message = "Escribí el nombre de la zona.") @Size(max = 50, message = "El nombre puede tener hasta 50 letras.") String name,
    Integer position) {}
