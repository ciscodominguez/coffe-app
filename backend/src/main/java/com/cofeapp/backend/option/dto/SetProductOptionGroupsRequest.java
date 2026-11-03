package com.cofeapp.backend.option.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Desde el formulario del producto: qué grupos de opciones usa (reemplaza la lista). */
@Getter
@Setter
public class SetProductOptionGroupsRequest {

  @NotNull
  private List<String> groupIds;
}
