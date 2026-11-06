package com.cofeapp.backend.legal.dto;

import jakarta.validation.constraints.NotBlank;

/** La versión que el dueño leyó y acepta (tiene que ser la vigente). */
public record AcceptTermsRequest(@NotBlank String version) {}
