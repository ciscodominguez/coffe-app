package com.cofeapp.backend.discount;

import com.cofeapp.backend.common.exception.BadRequestException;
import com.cofeapp.backend.common.exception.ConflictException;
import com.cofeapp.backend.common.exception.NotFoundException;
import com.cofeapp.backend.discount.dto.DiscountResponse;
import com.cofeapp.backend.discount.dto.SaveDiscountRequest;
import com.cofeapp.backend.domain.Discount;
import com.cofeapp.backend.domain.enums.DiscountType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La lista de descuentos del negocio (V12): "Jubilados 10%", "Cortesía
 * $500"… Se aplican al cobrar (ver {@code OrderService#applyDiscount}).
 * Nunca se borran: se desactivan, así los pedidos viejos siguen apuntando a
 * algo (igual guardan su propia copia del nombre y el valor).
 */
@Service
@RequiredArgsConstructor
public class DiscountService {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private final DiscountRepository discountRepository;

  public List<DiscountResponse> findAll() {
    return discountRepository.findAllByOrderByNameAsc().stream().map(DiscountResponse::from).toList();
  }

  @Transactional
  public DiscountResponse create(SaveDiscountRequest request) {
    Discount discount = new Discount();
    apply(discount, request);
    return DiscountResponse.from(discountRepository.save(discount));
  }

  @Transactional
  public DiscountResponse update(String id, SaveDiscountRequest request) {
    Discount discount = findOrThrow(id);
    apply(discount, request);
    return DiscountResponse.from(discountRepository.save(discount));
  }

  /** Baja lógica: deja de ofrecerse al cobrar. */
  @Transactional
  public void deactivate(String id) {
    Discount discount = findOrThrow(id);
    discount.setIsActive(false);
    discountRepository.save(discount);
  }

  private void apply(Discount discount, SaveDiscountRequest request) {
    String name = request.getName().trim();
    boolean taken =
        discountRepository.findAllByNameIgnoreCaseScoped(name).stream()
            .anyMatch(other -> !Objects.equals(other.getId(), discount.getId()));
    if (taken) {
      throw new ConflictException("Ya hay un descuento llamado \"" + name + "\".");
    }
    if (request.getType() == DiscountType.PERCENTAGE && request.getValue().compareTo(HUNDRED) > 0) {
      throw new BadRequestException("Un descuento en porcentaje no puede pasar del 100%.");
    }
    discount.setName(name);
    discount.setType(request.getType());
    discount.setValue(request.getValue());
    discount.setRequiresAuthorization(Boolean.TRUE.equals(request.getRequiresAuthorization()));
    discount.setIsActive(request.getIsActive() == null || request.getIsActive());
  }

  private Discount findOrThrow(String id) {
    return discountRepository
        .findByIdScoped(id)
        .orElseThrow(() -> new NotFoundException("No se encontró el descuento " + id + "."));
  }
}
