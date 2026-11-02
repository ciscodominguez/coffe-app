package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.TenantFilters;
import com.cofeapp.backend.common.tenant.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * Una línea del "conteo de efectivo por billete" al cerrar un turno de caja
 * — mejora pedida por Fran para hacer la caja "más profesional" ("conteo de
 * efectivo por billete" en vez de un único monto suelto). Una fila por cada
 * denominación contada (ej. "8 billetes de $2.000"), nunca una por billete
 * físico.
 *
 * <p>{@code denomination} está restringido en la migración (CHECK) a los
 * valores de billete vigentes en Argentina (ver {@code
 * V5__cash_session_denominations.sql}) — las monedas y los billetes chicos
 * quedan afuera a propósito: son inviables para el conteo de caja de un
 * café por la devaluación (ver el Javadoc de {@code
 * CashSessionService#close} sobre la investigación que confirmó esto).
 *
 * <p>{@code subtotal} = {@code denomination * quantity}, calculado en Java
 * al cerrar (no columna generada, a diferencia de {@code
 * CashSession#openLocationId} — acá no hace falta el truco porque no hay
 * ningún índice único involucrado) pero igual verificado con un CHECK
 * multi-columna en la migración, así una fila corrupta nunca puede
 * persistirse ni siquiera si un bug futuro la arma mal.
 *
 * <p>Sin endpoint de edición/baja a propósito: es un registro contable del
 * cierre, mismo criterio que {@link OrderItem}/{@link CashMovement}/{@link
 * OrderPayment} — si se cerró mal, se abre y cierra un turno nuevo, nunca
 * se edita el conteo de uno ya cerrado.
 *
 * <p>Entidad tenant-scoped: agrega acá {@code @Filter} (ver el Javadoc de
 * {@link TenantScopedEntity}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cash_session_denominations")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class CashSessionDenomination extends TenantScopedEntity {

  @Column(name = "cash_session_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String cashSessionId;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal denomination;

  @Column(nullable = false)
  private Integer quantity;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal subtotal;
}
