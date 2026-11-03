package com.cofeapp.backend.option.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Todos los grupos del negocio, en el orden en que se tienen que preguntar. */
@Getter
@Setter
public class ReorderOptionGroupsRequest {

  @NotNull
  private List<String> groupIds;
}
