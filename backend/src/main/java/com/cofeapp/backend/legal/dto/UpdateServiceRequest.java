package com.cofeapp.backend.legal.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Fran marca un pedido como atendido (o lo vuelve a pendiente) y deja una nota. */
public record UpdateServiceRequest(
    @Pattern(regexp = "PENDING|DONE", message = "Estado inválido.") String status, @Size(max = 1000) String adminNote) {}
