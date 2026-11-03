package com.cofeapp.backend.option;

import com.cofeapp.backend.domain.ProductOptionGroup;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOptionGroupRepository extends JpaRepository<ProductOptionGroup, String> {

  List<ProductOptionGroup> findAllByProductId(String productId);

  List<ProductOptionGroup> findAllByGroupId(String groupId);

  List<ProductOptionGroup> findAllByGroupIdIn(Collection<String> groupIds);
}
