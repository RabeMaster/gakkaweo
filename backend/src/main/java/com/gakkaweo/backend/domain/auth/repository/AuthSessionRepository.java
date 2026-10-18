package com.gakkaweo.backend.domain.auth.repository;

import com.gakkaweo.backend.domain.auth.entity.AuthSession;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

  @Query(
      "SELECT m.publicId AS publicId, m.role AS role, m.banned AS banned,"
          + " s.revoked AS revoked, s.expiresAt AS expiresAt"
          + " FROM AuthSession s JOIN s.member m"
          + " WHERE s.publicId = :publicId")
  Optional<AuthenticationState> findAuthenticationStateByPublicId(@Param("publicId") UUID publicId);

  Optional<AuthSession> findByPublicId(UUID publicId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM AuthSession s WHERE s.id = :id")
  Optional<AuthSession> findByIdForUpdate(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM AuthSession s WHERE s.publicId = :publicId")
  Optional<AuthSession> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

  List<AuthSession> findByMemberId(Long memberId);

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM AuthSession s WHERE s.member.id = :memberId")
  int deleteByMemberId(@Param("memberId") Long memberId);

  @Query("SELECT DISTINCT s.member.id FROM AuthSession s WHERE s.expiresAt < :now")
  List<Long> findExpiredMemberIds(@Param("now") Instant now);

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM AuthSession s WHERE s.member.id = :memberId AND s.expiresAt < :now")
  int deleteExpiredByMemberId(@Param("memberId") Long memberId, @Param("now") Instant now);
}
