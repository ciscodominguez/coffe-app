package com.cofeapp.backend.config;

import com.cofeapp.backend.common.tenant.TenantSafeRepositoryImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Registra {@link TenantSafeRepositoryImpl} como implementación base de
 * TODOS los repositorios Spring Data del proyecto — es lo que activa el
 * bloqueo de {@code findById}/{@code getReferenceById} sobre entidades
 * tenant-scoped (ver el Javadoc de esa clase).
 */
@Configuration
@EnableJpaRepositories(
    basePackages = "com.cofeapp.backend",
    repositoryBaseClass = TenantSafeRepositoryImpl.class)
public class JpaConfig {}
