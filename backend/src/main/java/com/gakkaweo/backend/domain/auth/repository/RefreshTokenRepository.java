package com.gakkaweo.backend.domain.auth.repository;

import com.gakkaweo.backend.domain.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  @EntityGraph(attributePaths = "member")
  Optional<RefreshToken> findByTokenHash(String tokenHash);

  @Query(
      "SELECT r.member.id AS memberId, r.session.id AS sessionId"
          + " FROM RefreshToken r"
          + " WHERE r.tokenHash = :tokenHash")
  Optional<TokenReference> findReferenceByTokenHash(@Param("tokenHash") String tokenHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM RefreshToken r WHERE r.tokenHash = :tokenHash")
  Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

  List<RefreshToken> findBySessionId(Long sessionId);

  List<RefreshToken> findByMemberId(Long memberId);

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM RefreshToken r WHERE r.member.id = :memberId")
  int deleteByMemberId(@Param("memberId") Long memberId);

  @Query("SELECT DISTINCT r.member.id FROM RefreshToken r WHERE r.expiresAt < :now")
  List<Long> findExpiredMemberIds(@Param("now") Instant now);

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM RefreshToken r WHERE r.member.id = :memberId AND r.expiresAt < :now")
  int deleteExpiredByMemberId(@Param("memberId") Long memberId, @Param("now") Instant now);
}
