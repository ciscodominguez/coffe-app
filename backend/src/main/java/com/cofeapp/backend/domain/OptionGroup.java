package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.TenantFilters;
import com.cofeapp.backend.common.tenant.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * Un grupo de opciones reutilizable (V11) — ej. "Leche", "Tamaño",
 * "Extras". Se crea una vez y se asigna a todos los productos que lo usan
 * ({@link ProductOptionGroup}); sus opciones viven en {@link OptionItem}.
 *
 * <p>{@code minSelect}/{@code maxSelect}: cuántas opciones se pueden elegir
 * al pedir. 0/1 = opcional, una sola ("Leche"); 1/1 = obligatorio, una sola
 * ("Tamaño"); 0/null = las que quieras ("Extras").
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "option_groups")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class OptionGroup extends TenantScopedEntity {

  @Column(nullable = false, length = 100)
  private String name;

  @Column(name = "min_select", nullable = false)
  private Integer minSelect = 0;

  /** Nulo = sin límite. */
  @Column(name = "max_select")
  private Integer maxSelect = 1;

  /** Orden en que se preguntan al tomar el pedido. */
  @Column(nullable = false)
  private Integer position = 0;
}
