package com.cofeapp.backend.table;

import com.cofeapp.backend.table.dto.CreateTableRequest;
import com.cofeapp.backend.table.dto.TableResponse;
import com.cofeapp.backend.table.dto.UpdateTableRequest;
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
 * Mismo criterio de roles que {@code CategoryController}/{@code
 * ProductController}: lectura abierta a cualquier rol autenticado (mesera/
 * KDS necesitan ver el estado de las mesas), escritura restringida a
 * OWNER/ADMIN — incluido {@code update}, aunque cambie {@code status}: el
 * cambio de estado "operativo" del día a día (ocupar/liberar una mesa al
 * abrir/cerrar un pedido) va a venir de {@code OrderService} en el Bloque
 * 3, no de que una mesera pegue manual acá. Este endpoint es para gestión
 * (alta de mesas nuevas, marcar una reservada a mano, etc.).
 */
@RestController
@RequestMapping("/tables")
@RequiredArgsConstructor
public class TableController {

  private final TableService tableService;

  /** {@code ?locationId=...} opcional — sin él, devuelve todas las mesas del negocio. */
  @GetMapping
  public List<TableResponse> findAll(@RequestParam(required = false) String locationId) {
    return locationId != null ? tableService.findAllByLocation(locationId) : tableService.findAll();
  }

  @GetMapping("/{id}")
  public TableResponse findOne(@PathVariable String id) {
    return tableService.findOne(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public TableResponse create(@Valid @RequestBody CreateTableRequest request) {
    return tableService.create(request);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public TableResponse update(@PathVariable String id, @Valid @RequestBody UpdateTableRequest request) {
    return tableService.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public void deactivate(@PathVariable String id) {
    tableService.deactivate(id);
  }
}
