package com.cofeapp.backend.discount;

import com.cofeapp.backend.discount.dto.DiscountResponse;
import com.cofeapp.backend.discount.dto.SaveDiscountRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Lista de descuentos (V12): cualquiera la lee (la ventana de cobro la muestra), solo OWNER/ADMIN la edita. */
@RestController
@RequestMapping("/discounts")
@RequiredArgsConstructor
public class DiscountController {

  private final DiscountService discountService;

  @GetMapping
  public List<DiscountResponse> findAll() {
    return discountService.findAll();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public DiscountResponse create(@Valid @RequestBody SaveDiscountRequest request) {
    return discountService.create(request);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public DiscountResponse update(@PathVariable String id, @Valid @RequestBody SaveDiscountRequest request) {
    return discountService.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN')")
  public void deactivate(@PathVariable String id) {
    discountService.deactivate(id);
  }
}
