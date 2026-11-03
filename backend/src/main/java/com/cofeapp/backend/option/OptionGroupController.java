package com.cofeapp.backend.option;

import com.cofeapp.backend.option.dto.OptionGroupResponse;
import com.cofeapp.backend.option.dto.ReorderOptionGroupsRequest;
import com.cofeapp.backend.option.dto.SaveOptionGroupRequest;
import com.cofeapp.backend.option.dto.SetProductOptionGroupsRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Grupos de opciones (V11). Mismo criterio de roles que el catálogo
 * ({@code ProductController}): cualquiera los lee (la pantalla de pedidos
 * los necesita), solo OWNER/ADMIN los edita.
 */
@RestController
@RequiredArgsConstructor
public class OptionGroupController {

  private final OptionGroupService optionGroupService;

  @GetMapping("/option-groups")
  public List<OptionGroupResponse> findAll() {
    return optionGroupService.findAll();
  }

  @GetMapping("/option-groups/{id}")
  public OptionGroupResponse findOne(@PathVariable String id) {
    return optionGroupService.findOne(id);
  }

  @PostMapping("/option-groups")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public OptionGroupResponse create(@Valid @RequestBody SaveOptionGroupRequest request) {
    return optionGroupService.create(request);
  }

  /** Orden en que se preguntan al tomar el pedido: todos los ids, en el orden nuevo. */
  @PutMapping("/option-groups/order")
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public List<OptionGroupResponse> reorder(@Valid @RequestBody ReorderOptionGroupsRequest request) {
    return optionGroupService.reorder(request.getGroupIds());
  }

  @PutMapping("/option-groups/{id}")
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public OptionGroupResponse update(@PathVariable String id, @Valid @RequestBody SaveOptionGroupRequest request) {
    return optionGroupService.update(id, request);
  }

  @DeleteMapping("/option-groups/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public void delete(@PathVariable String id) {
    optionGroupService.delete(id);
  }

  /** Desde el formulario del producto: qué grupos usa. */
  @PutMapping("/products/{productId}/option-groups")
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public Map<String, List<String>> setProductGroups(
      @PathVariable String productId, @Valid @RequestBody SetProductOptionGroupsRequest request) {
    return Map.of("groupIds", optionGroupService.setProductGroups(productId, request.getGroupIds()));
  }
}
