package com.cofeapp.backend.floor;

import com.cofeapp.backend.domain.FloorItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FloorItemRepository extends JpaRepository<FloorItem, String> {

  List<FloorItem> findAllByAreaIdIn(Collection<String> areaIds);

  List<FloorItem> findAllByAreaId(String areaId);
}
