package com.cofeapp.backend.user;

import com.cofeapp.backend.common.context.TenantContext;
import com.cofeapp.backend.common.exception.ConflictException;
import com.cofeapp.backend.common.exception.ForbiddenException;
import com.cofeapp.backend.common.exception.NotFoundException;
import com.cofeapp.backend.domain.User;
import com.cofeapp.backend.domain.enums.UserRole;
import com.cofeapp.backend.user.dto.CreateUserRequest;
import com.cofeapp.backend.user.dto.UpdateUserRequest;
import com.cofeapp.backend.user.dto.UserResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nótese que en ningún método de acá se pasa {@code businessId} a mano:
 * toda lectura/escritura pasa por {@code UserRepository}, cuyas queries
 * (derivadas o {@code @Query}/JPQL) quedan filtradas automáticamente por
 * el {@code @Filter} de Hibernate habilitado para la request (ver {@link
 * com.cofeapp.backend.common.tenant.TenantFilterInterceptor}) y cuyos
 * {@code save()} auto-asignan {@code businessId} vía {@code @PrePersist}
 * (ver {@link com.cofeapp.backend.common.tenant.TenantScopedEntity}). Este
 * service ni siquiera necesita saber cuál es el {@code businessId} actual
 * para quedar correctamente aislado.
 */
@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final TenantContext tenantContext;

  @Transactional
  public UserResponse create(CreateUserRequest request) {
    assertCanAssignRole(request.getRole(), null);

    User user = new User();
    user.setEmail(request.getEmail().trim().toLowerCase());
    user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
    user.setFirstName(request.getFirstName());
    user.setLastName(request.getLastName());
    user.setRole(request.getRole());
    user.setLocationId(request.getLocationId());

    try {
      return UserResponse.from(userRepository.saveAndFlush(user));
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException("Ya existe un usuario registrado con ese email.");
    }
  }

  public List<UserResponse> findAll(boolean includeInactive) {
    List<User> users =
        includeInactive
            ? userRepository.findAllByOrderByCreatedAtAsc()
            : userRepository.findAllByIsActiveTrueOrderByCreatedAtAsc();
    return users.stream().map(UserResponse::from).toList();
  }

  public UserResponse findOne(String id) {
    return UserResponse.from(findOneOrThrow(id));
  }

  @Transactional
  public UserResponse update(String id, UpdateUserRequest request) {
    User existing = findOneOrThrow(id);

    // V9: cambiarle la contraseña a alguien o darlo de baja/alta es
    // "gestionarlo": un ADMIN no puede hacerlo sobre un OWNER/ADMIN (mismo
    // criterio que asignar esos roles). La propia contraseña se cambia con
    // POST /auth/change-password (pide la actual), no por acá.
    if (request.getPassword() != null || request.getIsActive() != null) {
      if (existing.getId().equals(tenantContext.getUserId())) {
        throw new ForbiddenException("Tu propia contraseña se cambia desde \"Mi cuenta\".");
      }
      assertCanAssignRole(existing.getRole(), existing.getRole());
    }

    if (request.getRole() != null) {
      assertCanAssignRole(request.getRole(), existing.getRole());
    }

    if (request.getEmail() != null) existing.setEmail(request.getEmail().trim().toLowerCase());
    if (request.getFirstName() != null) existing.setFirstName(request.getFirstName());
    if (request.getLastName() != null) existing.setLastName(request.getLastName());
    if (request.getRole() != null) existing.setRole(request.getRole());
    if (request.getLocationId() != null) existing.setLocationId(request.getLocationId());
    if (request.getIsActive() != null) {
      if (Boolean.FALSE.equals(request.getIsActive())) existing.revokeSessions(); // V9: afuera al instante.
      existing.setIsActive(request.getIsActive());
    }
    if (request.getPassword() != null) {
      existing.setPasswordHash(passwordEncoder.encode(request.getPassword()));
      existing.revokeSessions(); // V9: tiene que volver a entrar con la contraseña nueva.
    }

    try {
      return UserResponse.from(userRepository.saveAndFlush(existing));
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException("Ya existe un usuario registrado con ese email.");
    }
  }

  /**
   * Baja lógica: un {@code User} nunca se borra físicamente porque
   * {@code Order.createdBy} lo referencia con {@code ON DELETE RESTRICT}
   * (Etapa 2) — un mesero que ya generó pedidos no puede desaparecer de la
   * base sin romper el historial de ventas.
   */
  @Transactional
  public UserResponse deactivate(String id) {
    User existing = findOneOrThrow(id);

    if (existing.getId().equals(tenantContext.getUserId())) {
      throw new ForbiddenException("No podés desactivar tu propio usuario.");
    }

    assertCanAssignRole(existing.getRole(), existing.getRole());
    existing.setIsActive(false);
    existing.revokeSessions(); // V9: sale de la app al instante, no cuando le vence el token.
    return UserResponse.from(userRepository.save(existing));
  }

  private User findOneOrThrow(String id) {
    // findByIdScoped (JPQL), no findById: ver TenantScopedEntity/
    // TenantSafeRepositoryImpl — es lo que hace que pedir el id de un
    // usuario de OTRO negocio dé "no encontrado" en vez de sus datos.
    return userRepository
        .findByIdScoped(id)
        .orElseThrow(() -> new NotFoundException("No se encontró el usuario " + id + "."));
  }

  /**
   * Solo un OWNER puede otorgar o modificar el rol OWNER/ADMIN. Un ADMIN
   * puede gestionar SUPERVISOR, WAITER y KITCHEN, pero no puede crear ni
   * editar otro OWNER/ADMIN — evita que un ADMIN se autopromueva
   * indirectamente a través de otra cuenta.
   */
  private void assertCanAssignRole(UserRole targetRole, UserRole currentRole) {
    UserRole actingRole = UserRole.valueOf(tenantContext.getRole());

    if (actingRole == UserRole.OWNER) return;

    if (actingRole == UserRole.ADMIN) {
      boolean targetRestricted = targetRole == UserRole.OWNER || targetRole == UserRole.ADMIN;
      boolean currentRestricted = currentRole == UserRole.OWNER || currentRole == UserRole.ADMIN;
      if (targetRestricted || currentRestricted) {
        throw new ForbiddenException("Un ADMIN no puede asignar ni modificar los roles OWNER o ADMIN.");
      }
      return;
    }

    throw new ForbiddenException("No tenés permisos para gestionar usuarios.");
  }
}
