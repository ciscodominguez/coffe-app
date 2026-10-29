package com.cofeapp.backend.user;

import com.cofeapp.backend.user.dto.CreateUserRequest;
import com.cofeapp.backend.user.dto.UpdateUserRequest;
import com.cofeapp.backend.user.dto.UserResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lectura restringida en el BACKEND a OWNER/ADMIN/SUPERVISOR (la lista de
 * usuarios trae emails y roles de todo el personal) — mismo grupo que ve la
 * pantalla {@code /users} en el frontend. Ninguna otra pantalla necesita
 * esta API: los nombres que muestran Caja y Pedidos ("abrió X", "cobró Y")
 * ya los resuelve el backend dentro de cada respuesta ({@code
 * UserRepository#findAllByIdInScoped}), sin pasar por acá. Alta/edición/
 * baja siguen siendo solo OWNER/ADMIN.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
    return userService.create(request);
  }

  @GetMapping
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN', 'SUPERVISOR')")
  public List<UserResponse> findAll(
      @RequestParam(name = "includeInactive", required = false, defaultValue = "false") boolean includeInactive) {
    return userService.findAll(includeInactive);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN', 'SUPERVISOR')")
  public UserResponse findOne(@PathVariable String id) {
    return userService.findOne(id);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public UserResponse update(@PathVariable String id, @Valid @RequestBody UpdateUserRequest request) {
    return userService.update(id, request);
  }

  /** Baja lógica (ver {@code UserService#deactivate}) — nunca un DELETE físico. */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public UserResponse deactivate(@PathVariable String id) {
    return userService.deactivate(id);
  }
}
