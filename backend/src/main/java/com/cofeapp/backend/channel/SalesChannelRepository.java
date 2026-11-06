package com.cofeapp.backend.channel;

import com.cofeapp.backend.domain.SalesChannel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesChannelRepository extends JpaRepository<SalesChannel, String> {

  /** Reemplaza a {@code findById()} (bloqueado para entidades de un café). */
  @Query("select c from SalesChannel c where c.id = :id")
  Optional<SalesChannel> findByIdScoped(@Param("id") String id);

  List<SalesChannel> findAllByLocationIdOrderByCreatedAtAsc(String locationId);

  List<SalesChannel> findAllByOrderByCreatedAtAsc();

  boolean existsByLocationIdAndNameIgnoreCase(String locationId, String name);

  boolean existsByLocationIdAndNameIgnoreCaseAndIdNot(String locationId, String name, String id);
}
