package com.cofeapp.backend.domain;

import com.cofeapp.backend.common.tenant.TenantFilters;
import com.cofeapp.backend.common.tenant.TenantScopedEntity;
import com.cofeapp.backend.domain.enums.QrMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Sucursal de un negocio. Existe desde la Etapa 1 aunque el MVP solo use
 * una por tenant.
 *
 * <p>Entidad tenant-scoped: agrega acá {@code @FilterDef}/{@code @Filter}
 * (ver el Javadoc de {@link TenantScopedEntity} sobre por qué esto se
 * repite en cada entidad concreta en vez de heredarse implícitamente).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "locations")
@Filter(name = TenantFilters.BUSINESS_FILTER, condition = "business_id = :businessId")
public class Location extends TenantScopedEntity {

  @Column(nullable = false, length = 150)
  private String name;

  @Column(length = 255)
  private String address;

  @Column(length = 50)
  private String phone;

  /**
   * Ancho del rollo de la ticketera de este local (58 u 80 mm) — lo usa el
   * frontend para armar comandas/tickets/cierres de caja con el ancho
   * correcto al imprimir desde el navegador (V7). Validado en {@code
   * LocationService#update} y por un CHECK en la base.
   *
   * <p>{@code @JdbcTypeCode(SMALLINT)}: la columna es SMALLINT en la V7, y
   * sin esto Hibernate espera INTEGER para un {@code Integer} y la
   * validación del esquema ({@code ddl-auto: validate}) frena el arranque.
   */
  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(name = "receipt_paper_width_mm", nullable = false)
  private Integer receiptPaperWidthMm = 80;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  // --- Cobro con QR de Mercado Pago (V10) — ver MercadoPagoAccountService. ---

  /** DYNAMIC = QR nuevo en pantalla por cobro; STATIC = QR impreso en el mostrador. */
  @Enumerated(EnumType.STRING)
  @Column(name = "mp_qr_mode", nullable = false, length = 10)
  private QrMode mpQrMode = QrMode.DYNAMIC;

  /** Sucursal creada en la cuenta de MP para este local (nula hasta configurarlo). */
  @Column(name = "mp_store_id", length = 30)
  private String mpStoreId;

  @Column(name = "mp_pos_id", length = 30)
  private String mpPosId;

  /** El "external_pos_id" con el que se crean las órdenes QR de este local. */
  @Column(name = "mp_pos_external_id", length = 40)
  private String mpPosExternalId;

  /** Imagen del QR fijo de la caja (para imprimirlo y pegarlo en el mostrador). */
  @Column(name = "mp_qr_image_url", length = 500)
  private String mpQrImageUrl;

  @PrePersist
  private void defaults() {
    if (isActive == null) isActive = true;
    if (receiptPaperWidthMm == null) receiptPaperWidthMm = 80;
    if (mpQrMode == null) mpQrMode = QrMode.DYNAMIC;
  }
}
