package com.cofeapp.backend.auth;

import com.cofeapp.backend.common.exception.ForbiddenException;
import com.cofeapp.backend.common.security.JwtService;
import com.cofeapp.backend.common.security.SessionValidator;
import com.cofeapp.backend.domain.PlatformAdmin;
import com.cofeapp.backend.platform.PlatformAdminRepository;
import com.cofeapp.backend.platform.PlatformProperties;
import com.cofeapp.backend.auth.dto.AuthUserDto;
import com.cofeapp.backend.domain.Business;

import com.cofeapp.backend.auth.dto.AuthResponse;
import com.cofeapp.backend.auth.dto.AuthUserDto;
import com.cofeapp.backend.auth.dto.ChangePasswordRequest;
import com.cofeapp.backend.auth.dto.LoginRequest;
import com.cofeapp.backend.auth.dto.RegisterBusinessRequest;
import com.cofeapp.backend.billing.BillingService;
import com.cofeapp.backend.business.BusinessRepository;
import com.cofeapp.backend.legal.LegalService;
import com.cofeapp.backend.common.context.TenantContext;
import com.cofeapp.backend.common.exception.BadRequestException;
import com.cofeapp.backend.common.exception.ConflictException;
import com.cofeapp.backend.common.exception.NotFoundException;
import com.cofeapp.backend.common.security.AuthenticatedUser;
import com.cofeapp.backend.common.security.JwtService;
import com.cofeapp.backend.domain.Business;
import com.cofeapp.backend.domain.Location;
import com.cofeapp.backend.domain.User;
import com.cofeapp.backend.domain.enums.UserRole;
import com.cofeapp.backend.location.LocationRepository;
import com.cofeapp.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final BusinessRepository businessRepository;
  private final LocationRepository locationRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final TenantContext tenantContext;
  private final LoginAttemptService loginAttemptService;
  private final PlatformAdminRepository platformAdminRepository;
  private final PlatformProperties platformProperties;
  private final BillingService billingService;
  private final LegalService legalService;

  /**
   * Único punto del sistema que crea un {@code Business}. Es una operación
   * "pre-tenant": todavía no existe el negocio cuando arranca.
   *
   * <p>Todo el método corre dentro de UNA sola transacción
   * ({@code @Transactional}) — si falla la creación del {@code Location} o
   * del {@code User} (ej. email ya usado por otro negocio, único a nivel
   * global), la base deshace también el {@code Business} recién insertado,
   * sin dejar un tenant huérfano. Por eso no hace falta una compensación
   * manual (borrar el negocio a mano en un catch): el rollback automático
   * de Spring ante cualquier excepción no controlada alcanza y es más
   * seguro (sin ventana de carrera entre un delete de compensación y una
   * lectura concurrente del negocio a medio crear).
   */
  @Transactional
  public AuthResponse registerBusiness(RegisterBusinessRequest request) {
    String normalizedEmail = request.getOwnerEmail().trim().toLowerCase();
    // V15: el email del superadmin queda reservado (si no, ese dueño
    // nunca podría entrar: el login lo tomaría como superadmin).
    if (platformAdminRepository.findByEmail(normalizedEmail).isPresent()) {
      throw new ConflictException("Ese email ya está registrado.");
    }

    Business business = new Business();
    business.setLegalName(request.getLegalName());
    business.setTradeName(request.getTradeName());
    business.setTaxId(request.getTaxId());
    business.setFiscalCondition(request.getFiscalCondition());
    // V16 — prueba gratis (14 días por defecto; se cambia en el panel).
    business.setTrialEndsAt(billingService.newTrialEnd());

    try {
      business = businessRepository.saveAndFlush(business);
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException("Ya existe un negocio registrado con ese CUIT.");
    }

    String passwordHash = passwordEncoder.encode(request.getOwnerPassword());

    // Abre el contexto de tenant A MANO, apuntando al negocio recién
    // creado. Es el único lugar del sistema fuera de
    // JwtAuthenticationFilter que llama a TenantContext.open/clear
    // directamente.
    tenantContext.open(
        new TenantContext.TenantStore(
            business.getId(), null, "bootstrap", UserRole.OWNER.name(), normalizedEmail));
    try {
      Location location = new Location();
      location.setName(
          request.getTradeName() != null ? request.getTradeName() : request.getLegalName());
      // @PrePersist de TenantScopedEntity toma el businessId del contexto
      // recién abierto — nunca se setea acá a mano.
      locationRepository.save(location);

      User owner = new User();
      owner.setEmail(normalizedEmail);
      owner.setPasswordHash(passwordHash);
      owner.setFirstName(request.getOwnerFirstName());
      owner.setLastName(request.getOwnerLastName());
      owner.setRole(UserRole.OWNER);

      try {
        owner = userRepository.saveAndFlush(owner);
      } catch (DataIntegrityViolationException ex) {
        throw new ConflictException("Ya existe un usuario registrado con ese email.");
      }

      // V17 — constancia de que aceptó los términos y la privacidad (la casilla).
      legalService.record(business, owner.getId());

      return issueToken(owner);
    } finally {
      tenantContext.clear();
    }
  }

  public AuthResponse login(LoginRequest request) {
    String normalizedEmail = request.getEmail().trim().toLowerCase();

    // findByEmail, no findByIdScoped: acá TODAVÍA no hay contexto de
    // tenant (es justo lo que este método está por descubrir), así que el
    // filtro de Hibernate está deshabilitado — esta consulta corre "cruda"
    // sobre toda la tabla users.
    // V9: freno contra adivinar contraseñas — ver LoginAttemptService.
    loginAttemptService.assertNotLocked(normalizedEmail);

    // V15 — ¿es la cuenta de superadmin? (tabla aparte, sin café).
    PlatformAdmin admin = platformAdminRepository.findByEmail(normalizedEmail).orElse(null);
    if (admin != null
        && Boolean.TRUE.equals(admin.getIsActive())
        && passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
      loginAttemptService.loginSucceeded(normalizedEmail);
      int version = admin.getTokenVersion() == null ? 0 : admin.getTokenVersion();
      String token = jwtService.generatePlatformToken(admin.getId(), admin.getEmail(), version);
      return new AuthResponse(
          token,
          new AuthUserDto(
              admin.getId(), admin.getEmail(), admin.getFirstName(), admin.getLastName(), JwtService.SUPERADMIN_ROLE, null, null));
    }

    User user = userRepository.findByEmail(normalizedEmail).orElse(null);

    if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
      loginAttemptService.loginFailed(normalizedEmail);
      throw new BadCredentialsException("Email o contraseña incorrectos.");
    }

    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      loginAttemptService.loginFailed(normalizedEmail);
      throw new BadCredentialsException("Email o contraseña incorrectos.");
    }

    loginAttemptService.loginSucceeded(normalizedEmail);

    // V15 — café suspendido o dado de baja: no entra nadie.
    Business business = businessRepository.findById(user.getBusinessId()).orElse(null);
    if (business != null && business.isBlocked()) {
      // V16 — suspendido por falta de pago: el dueño sí entra (solo a pagar
      // la suscripción, ver JwtAuthenticationFilter); el resto, no.
      boolean ownerMayPay = business.isBlockedForPayment() && user.getRole() == UserRole.OWNER;
      if (!ownerMayPay) throw new ForbiddenException(SessionValidator.blockedMessage(business, platformProperties));
    }
    return issueToken(user);
  }

  /**
   * Cambiar la propia contraseña (V9). Pide la actual, guarda la nueva y
   * cierra todas las demás sesiones abiertas de esta persona (otros
   * celulares/compus). Devuelve un token nuevo para que en ESTE dispositivo
   * siga adentro sin volver a loguearse.
   */
  @Transactional
  public AuthResponse changePassword(ChangePasswordRequest request) {
    User user =
        userRepository
            .findByIdScoped(tenantContext.getUserId())
            .orElseThrow(() -> new NotFoundException("No se encontró tu usuario."));

    if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
      throw new BadRequestException("La contraseña actual no es correcta.");
    }
    if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
      throw new BadRequestException("La contraseña nueva tiene que ser distinta de la actual.");
    }

    user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
    user.revokeSessions();
    return issueToken(userRepository.save(user));
  }

  private AuthResponse issueToken(User user) {
    AuthenticatedUser authenticatedUser =
        new AuthenticatedUser(
            user.getId(), user.getBusinessId(), user.getLocationId(), user.getRole().name(), user.getEmail());

    String accessToken =
        jwtService.generateToken(authenticatedUser, user.getTokenVersion() == null ? 0 : user.getTokenVersion());

    AuthUserDto userDto =
        new AuthUserDto(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getRole().name(),
            user.getBusinessId(),
            user.getLocationId());

    return new AuthResponse(accessToken, userDto);
  }
}
