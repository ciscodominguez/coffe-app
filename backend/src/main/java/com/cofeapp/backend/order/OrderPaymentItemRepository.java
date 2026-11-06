package com.cofeapp.backend.order;

import com.cofeapp.backend.domain.OrderPaymentItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderPaymentItemRepository extends JpaRepository<OrderPaymentItem, String> {

  List<OrderPaymentItem> findAllByOrderPaymentIdIn(Collection<String> orderPaymentIds);
}
