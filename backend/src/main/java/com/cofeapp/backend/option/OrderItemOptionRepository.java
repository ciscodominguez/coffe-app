package com.cofeapp.backend.option;

import com.cofeapp.backend.domain.OrderItemOption;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemOptionRepository extends JpaRepository<OrderItemOption, String> {

  List<OrderItemOption> findAllByOrderItemIdInOrderByPositionAsc(Collection<String> orderItemIds);
}
