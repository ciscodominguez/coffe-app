package com.cofeapp.backend.mercadopago;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita las tareas programadas (hoy, solo {@link QrChargeSweeper}). */
@Configuration
@EnableScheduling
public class MercadoPagoConfig {}
