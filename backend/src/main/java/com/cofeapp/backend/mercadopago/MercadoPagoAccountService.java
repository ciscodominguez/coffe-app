package com.cofeapp.backend.mercadopago;

import com.cofeapp.backend.common.context.TenantContext;
import com.cofeapp.backend.common.crypto.SecretCipher;
import com.cofeapp.backend.common.exception.BadRequestException;
import com.cofeapp.backend.common.exception.ConflictException;
import com.cofeapp.backend.common.exception.NotFoundException;
import com.cofeapp.backend.domain.Location;
import com.cofeapp.backend.domain.MercadoPagoAccount;
import com.cofeapp.backend.domain.enums.QrChargeStatus;
import com.cofeapp.backend.location.LocationRepository;
import com.cofeapp.backend.mercadopago.client.MercadoPagoApiException;
import com.cofeapp.backend.mercadopago.client.MercadoPagoClient;
import com.cofeapp.backend.mercadopago.client.MercadoPagoClient.MpPos;
import com.cofeapp.backend.mercadopago.client.MercadoPagoClient.MpPosRequest;
import com.cofeapp.backend.mercadopago.client.MercadoPagoClient.MpStore;
import com.cofeapp.backend.mercadopago.client.MercadoPagoClient.MpStoreRequest;
import com.cofeapp.backend.mercadopago.client.MercadoPagoClient.MpUser;
import com.cofeapp.backend.mercadopago.dto.ConfigureLocationQrRequest;
import com.cofeapp.backend.mercadopago.dto.MercadoPagoStatusResponse;
import com.cofeapp.backend.mercadopago.dto.MercadoPagoStatusResponse.LocationQrStatus;
import com.cofeapp.backend.mercadopago.dto.QrAvailabilityResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conexión de un negocio con su cuenta de Mercado Pago y configuración
 * del QR de cada local (V10).
 *
 * <p><b>Cómo se conecta:</b> el dueño pega el Access Token de producción de
 * su cuenta (Mercado Pago → Tus integraciones → credenciales). Se valida
 * contra Mercado Pago ({@code GET /users/me}) y se guarda cifrado. Más
 * adelante, con la app publicada, se puede sumar el botón "Conectar con
 * Mercado Pago" (OAuth) sin cambiar el resto: el token termina en el mismo
 * lugar.
 *
 * <p><b>Cada local = una caja de Mercado Pago:</b> la primera vez que se
 * configura un local se crea una sucursal y una caja en la cuenta del
 * negocio (MP pide la dirección y las coordenadas del local). Esa caja
 * trae un QR fijo para imprimir (modo STATIC) y también sirve para generar
 * QR en pantalla (modo DYNAMIC).
 */
@Service
@RequiredArgsConstructor
public class MercadoPagoAccountService {

  private final MercadoPagoAccountRepository accountRepository;
  private final MpQrChargeRepository chargeRepository;
  private final LocationRepository locationRepository;
  private final MercadoPagoClient mercadoPagoClient;
  private final SecretCipher secretCipher;
  private final TenantContext tenantContext;

  public MercadoPagoStatusResponse status() {
    Optional<MercadoPagoAccount> account = accountRepository.findByBusinessId(tenantContext.getBusinessId());
    List<LocationQrStatus> locations = new ArrayList<>();
    for (Location location : locationRepository.findAllByOrderByCreatedAtAsc()) {
      locations.add(
          new LocationQrStatus(
              location.getId(),
              location.getName(),
              location.getMpQrMode(),
              location.getMpPosExternalId() != null,
              location.getMpQrImageUrl()));
    }
    return account
        .map(
            a ->
                new MercadoPagoStatusResponse(
                    true, a.getMpNickname(), a.getMpUserId(), Boolean.TRUE.equals(a.getTestMode()), a.getUpdatedAt(), locations))
        .orElseGet(() -> new MercadoPagoStatusResponse(false, null, null, false, null, locations));
  }

  @Transactional
  public MercadoPagoStatusResponse connect(String rawToken) {
    String token = rawToken == null ? "" : rawToken.trim();
    if (!token.startsWith("APP_USR-") && !token.startsWith("TEST-")) {
      throw new BadRequestException(
          "Eso no parece un Access Token de Mercado Pago: tiene que empezar con APP_USR- (o TEST- si es de prueba).");
    }

    MpUser user;
    try {
      user = mercadoPagoClient.getMe(token);
    } catch (MercadoPagoApiException ex) {
      if (ex.isAuthError()) {
        throw new BadRequestException(
            "Mercado Pago rechazó ese token. Copiá de nuevo el Access Token de tu cuenta y fijate que esté completo.");
      }
      throw ex;
    }
    if (user.id() == null) {
      throw new MercadoPagoApiException(0, "Mercado Pago no devolvió los datos de la cuenta. Probá de nuevo en un rato.");
    }

    String businessId = tenantContext.getBusinessId();
    MercadoPagoAccount account = accountRepository.findByBusinessId(businessId).orElseGet(MercadoPagoAccount::new);

    boolean differentAccount = account.getMpUserId() != null && !account.getMpUserId().equals(user.id());
    if (differentAccount) {
      assertNoPendingCharges(businessId, "cambiar de cuenta");
      // Las cajas creadas en la cuenta anterior no existen en la nueva:
      // hay que volver a configurar cada local.
      for (Location location : locationRepository.findAllByOrderByCreatedAtAsc()) {
        clearMercadoPagoSetup(location);
      }
    }

    account.setBusinessId(businessId);
    account.setAccessTokenEncrypted(secretCipher.encrypt(token));
    account.setMpUserId(user.id());
    account.setMpNickname(user.nickname());
    account.setTestMode(token.startsWith("TEST-"));
    account.setConnectedById(tenantContext.getUserId());
    accountRepository.save(account);
    return status();
  }

  @Transactional
  public MercadoPagoStatusResponse disconnect() {
    String businessId = tenantContext.getBusinessId();
    MercadoPagoAccount account =
        accountRepository
            .findByBusinessId(businessId)
            .orElseThrow(() -> new NotFoundException("Mercado Pago no está conectado."));
    assertNoPendingCharges(businessId, "desconectar Mercado Pago");
    accountRepository.delete(account);
    // Las cajas de cada local se conservan: si se vuelve a conectar la misma
    // cuenta, siguen sirviendo (y si es otra, connect() las borra).
    return status();
  }

  @Transactional
  public MercadoPagoStatusResponse configureLocation(String locationId, ConfigureLocationQrRequest request) {
    MercadoPagoAccount account = requireAccount();
    Location location =
        locationRepository
            .findByIdScoped(locationId)
            .orElseThrow(() -> new NotFoundException("No se encontró el local " + locationId + "."));

    if (request.getQrMode() != null && request.getQrMode() != location.getMpQrMode()) {
      if (chargeRepository.existsByLocationIdAndStatus(location.getId(), QrChargeStatus.PENDING)) {
        throw new ConflictException(
            "Hay un cobro con QR esperando en este local. Esperá a que termine para cambiar el tipo de QR.");
      }
      location.setMpQrMode(request.getQrMode());
    }

    if (location.getMpPosExternalId() == null) {
      createStoreAndPos(account, location, request);
    }

    locationRepository.save(location);
    return status();
  }

  /**
   * Para la ventana de cobro (cualquier rol que cobra): ¿se puede cobrar
   * con QR en este local? Si no, el motivo en palabras simples.
   */
  public QrAvailabilityResponse availability(String locationId) {
    Location location =
        locationRepository
            .findByIdScoped(locationId)
            .orElseThrow(() -> new NotFoundException("No se encontró el local " + locationId + "."));
    Optional<MercadoPagoAccount> account = accountRepository.findByBusinessId(tenantContext.getBusinessId());
    if (account.isEmpty()) {
      return new QrAvailabilityResponse(
          false, location.getMpQrMode(), false, "Mercado Pago no está conectado. El dueño lo conecta en \"Cobros con QR\".");
    }
    boolean testMode = Boolean.TRUE.equals(account.get().getTestMode());
    if (location.getMpPosExternalId() == null) {
      return new QrAvailabilityResponse(
          false, location.getMpQrMode(), testMode, "Falta configurar el QR de este local en \"Cobros con QR\".");
    }
    return new QrAvailabilityResponse(true, location.getMpQrMode(), testMode, null);
  }

  /** Token en claro del negocio actual — solo para uso interno del backend. */
  MercadoPagoAccount requireAccount() {
    return accountRepository
        .findByBusinessId(tenantContext.getBusinessId())
        .orElseThrow(
            () -> new ConflictException("Mercado Pago no está conectado. El dueño lo conecta en \"Cobros con QR\"."));
  }

  String accessTokenOf(MercadoPagoAccount account) {
    return secretCipher.decrypt(account.getAccessTokenEncrypted());
  }

  // ---------------------------------------------------------------------

  private void createStoreAndPos(MercadoPagoAccount account, Location location, ConfigureLocationQrRequest request) {
    String token = accessTokenOf(account);
    String compactId = location.getId().replace("-", "").substring(0, 24);
    String storeExternalId = "CFS" + compactId;
    String posExternalId = "CFP" + compactId;

    if (location.getMpStoreId() == null) {
      List<String> missing = new ArrayList<>();
      if (isBlank(request.getStreetName())) missing.add("calle");
      if (isBlank(request.getStreetNumber())) missing.add("número");
      if (isBlank(request.getCityName())) missing.add("ciudad");
      if (isBlank(request.getStateName())) missing.add("provincia");
      if (request.getLatitude() == null || request.getLongitude() == null) missing.add("ubicación en el mapa (latitud y longitud)");
      if (!missing.isEmpty()) {
        throw new BadRequestException(
            "Para crear la caja en Mercado Pago falta completar: " + String.join(", ", missing) + ".");
      }
      MpStore store =
          mercadoPagoClient.createStore(
              token,
              account.getMpUserId(),
              new MpStoreRequest(
                  location.getName(),
                  storeExternalId,
                  request.getStreetName().trim(),
                  request.getStreetNumber().trim(),
                  request.getCityName().trim(),
                  request.getStateName().trim(),
                  request.getLatitude(),
                  request.getLongitude(),
                  isBlank(request.getReference()) ? location.getName() : request.getReference().trim()));
      location.setMpStoreId(store.id());
      // Se guarda enseguida: si la caja falla, al reintentar no se crea otra sucursal.
      locationRepository.saveAndFlush(location);
    }

    MpPos pos =
        mercadoPagoClient.createPos(
            token, new MpPosRequest(location.getName() + " - Caja", location.getMpStoreId(), storeExternalId, posExternalId));
    location.setMpPosId(pos.id());
    location.setMpPosExternalId(pos.externalId() != null ? pos.externalId() : posExternalId);
    location.setMpQrImageUrl(pos.qrImageUrl());
  }

  private void clearMercadoPagoSetup(Location location) {
    location.setMpStoreId(null);
    location.setMpPosId(null);
    location.setMpPosExternalId(null);
    location.setMpQrImageUrl(null);
    locationRepository.save(location);
  }

  private void assertNoPendingCharges(String businessId, String action) {
    if (chargeRepository.existsByBusinessIdAndStatus(businessId, QrChargeStatus.PENDING)) {
      throw new ConflictException(
          "Hay cobros con QR esperando el pago. Esperá a que terminen (o cancelalos) antes de " + action + ".");
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
