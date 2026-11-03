package com.cofeapp.backend.discount;

import com.cofeapp.backend.domain.Discount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiscountRepository extends JpaRepository<Discount, String> {

  /** Reemplaza a {@code findById()} (bloqueado por {@code TenantSafeRepositoryImpl} para esta entidad tenant-scoped). */
  @Query("select d from Discount d where d.id = :id")
  Optional<Discount> findByIdScoped(@Param("id") String id);

  List<Discount> findAllByOrderByNameAsc();

  @Query("select d from Discount d where lower(d.name) = lower(:name)")
  List<Discount> findAllByNameIgnoreCaseScoped(@Param("name") String name);
}
