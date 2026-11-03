package com.cofeapp.backend.auth;

import com.cofeapp.backend.domain.PasswordResetToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** {@link PasswordResetToken} no es tenant-scoped: estas búsquedas son globales a propósito (llegan sin sesión). */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {

  Optional<PasswordResetToken> findByTokenHash(String tokenHash);

  /** Al pedir un link nuevo, los anteriores de esa persona dejan de servir. */
  @Modifying
  @Query("delete from PasswordResetToken t where t.userId = :userId")
  void deleteAllByUserId(@Param("userId") String userId);
}
