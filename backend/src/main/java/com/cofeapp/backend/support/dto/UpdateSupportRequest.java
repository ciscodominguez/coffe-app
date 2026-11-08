package com.cofeapp.backend.support.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Fran marca un pedido de ayuda como atendido (o pendiente) y deja una nota. */
public record UpdateSupportRequest(
    @Pattern(regexp = "PENDING|DONE", message = "Estado inválido.") String status, @Size(max = 1000) String adminNote) {}
