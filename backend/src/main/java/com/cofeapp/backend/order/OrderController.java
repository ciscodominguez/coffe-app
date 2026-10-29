package com.cofeapp.backend.order;

import com.cofeapp.backend.order.dto.AddOrderItemsRequest;
import com.cofeapp.backend.order.dto.ApplyDiscountRequest;
import com.cofeapp.backend.order.dto.CreateOrderPaymentRequest;
import com.cofeapp.backend.order.dto.CreateOrderRequest;
import com.cofeapp.backend.order.dto.OrderResponse;
import com.cofeapp.backend.order.dto.UpdateOrderStatusRequest;
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
 * A diferencia de Category/Product/Table, acá la lectura también queda
 * abierta a cualquier rol autenticado (mesera y KDS necesitan listar/ver
 * pedidos por igual), pero la escritura se divide en dos permisos
 * distintos: crear un pedido es cosa de mesera/supervisor/admin/owner
 * (KITCHEN no toma pedidos), mientras que cambiar el estado lo puede pedir
 * cualquiera de esos roles MÁS KITCHEN — la validación fina de qué rol
 * puede hacer qué transición puntual (PENDING→IN_PREPARATION solo KITCHEN,
 * etc.) vive en {@code OrderService#assertTransitionAllowed}, no acá,
 * porque depende del estado ORIGEN del pedido y no solo del endpoint.
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderService orderService;

  /** {@code ?locationId=...} opcional — sin él, devuelve todos los pedidos del negocio. */
  @GetMapping
  public List<OrderResponse> findAll(@RequestParam(required = false) String locationId) {
    return locationId != null ? orderService.findAllByLocation(locationId) : orderService.findAll();
  }

  /**
   * Respaldo por polling para el KDS. {@code ?locationId=...} opcional,
   * mismo criterio que {@link #findAll} — el frontend de KDS SIEMPRE lo
   * manda (necesita el mismo subconjunto que el WebSocket), queda opcional
   * acá solo por consistencia con el resto de los endpoints de listado.
   */
  @GetMapping("/active")
  public List<OrderResponse> findActive(@RequestParam(required = false) String locationId) {
    return locationId != null ? orderService.findActiveByLocation(locationId) : orderService.findActive();
  }

  @GetMapping("/{id}")
  public OrderResponse findOne(@PathVariable String id) {
    return orderService.findOne(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyAuthority('WAITER', 'SUPERVISOR', 'ADMIN', 'OWNER')")
  public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
    try {
      return orderService.create(request);
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      // Mismo pedido enviado dos veces a la vez: devuelve el que se creó (ver OrderService#findByIdempotencyKey).
      return orderService.findByIdempotencyKey(request.getIdempotencyKey()).orElseThrow(() -> e);
    }
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAnyAuthority('WAITER', 'KITCHEN', 'SUPERVISOR', 'ADMIN', 'OWNER')")
  public OrderResponse updateStatus(
      @PathVariable String id, @Valid @RequestBody UpdateOrderStatusRequest request) {
    return orderService.updateStatus(id, request);
  }

  /**
   * Hoja de ruta, paso 1: sumar productos a un pedido abierto. Mismo grupo
   * de roles que {@link #create} — KITCHEN no carga pedidos.
   */
  @PostMapping("/{id}/items")
  @PreAuthorize("hasAnyAuthority('WAITER', 'SUPERVISOR', 'ADMIN', 'OWNER')")
  public OrderResponse addItems(@PathVariable String id, @Valid @RequestBody AddOrderItemsRequest request) {
    return orderService.addItems(id, request);
  }

  /**
   * V12 — aplicar un descuento (a un ítem o a todo el pedido). Los permisos
   * finos (a mano o "pide autorización" = supervisor para arriba) los
   * controla {@code OrderService#applyDiscount}.
   */
  @PostMapping("/{id}/discounts")
  @PreAuthorize("hasAnyAuthority('WAITER', 'SUPERVISOR', 'ADMIN', 'OWNER')")
  public OrderResponse applyDiscount(@PathVariable String id, @Valid @RequestBody ApplyDiscountRequest request) {
    return orderService.applyDiscount(id, request);
  }

  @DeleteMapping("/{id}/discounts/{discountId}")
  @PreAuthorize("hasAnyAuthority('WAITER', 'SUPERVISOR', 'ADMIN', 'OWNER')")
  public OrderResponse removeDiscount(@PathVariable String id, @PathVariable String discountId) {
    return orderService.removeDiscount(id, discountId);
  }

  /**
   * Ítems 5/11 de las mejoras pedidas — una línea de cobro por llamada
   * (pago dividido entre métodos: mandar una por cada método usado). Mismo
   * grupo de roles que {@link #create}: KITCHEN no maneja dinero.
   */
  @PostMapping("/{id}/payments")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyAuthority('WAITER', 'SUPERVISOR', 'ADMIN', 'OWNER')")
  public OrderResponse addPayment(@PathVariable String id, @Valid @RequestBody CreateOrderPaymentRequest request) {
    return orderService.addPayment(id, request);
  }
}
