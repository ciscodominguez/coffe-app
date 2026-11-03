package com.cofeapp.backend.option.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Crear o editar un grupo completo (nombre, reglas, opciones y a qué
 * productos se aplica) — se manda todo junto, como el formulario.
 */
@Getter
@Setter
public class SaveOptionGroupRequest {

  @NotBlank
  @Size(max = 100)
  private String name;

  /** 0 = opcional; 1 o más = hay que elegir al menos esa cantidad. */
  @NotNull
  @Min(0)
  private Integer minSelect;

  /** Nulo = sin límite. */
  @Min(1)
  private Integer maxSelect;

  @NotNull
  @Size(min = 1, max = 50, message = "Cargá al menos una opción (y hasta 50).")
  @Valid
  private List<OptionItemInput> options;

  /** Productos que usan este grupo (reemplaza la lista). Nulo = no se tocan. */
  private List<String> productIds;
}
