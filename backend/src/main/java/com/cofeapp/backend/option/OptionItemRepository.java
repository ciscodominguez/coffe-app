package com.cofeapp.backend.option;

import com.cofeapp.backend.domain.OptionItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OptionItemRepository extends JpaRepository<OptionItem, String> {

  List<OptionItem> findAllByGroupIdOrderByPositionAsc(String groupId);

  List<OptionItem> findAllByGroupIdInOrderByPositionAsc(Collection<String> groupIds);
}
