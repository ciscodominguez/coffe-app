package com.cofeapp.backend.billing;

import com.cofeapp.backend.domain.SubscriptionPayment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, String> {

  Optional<SubscriptionPayment> findByMpAuthorizedPaymentId(String mpAuthorizedPaymentId);

  List<SubscriptionPayment> findTop24ByBusinessIdOrderByChargedAtDesc(String businessId);
}
