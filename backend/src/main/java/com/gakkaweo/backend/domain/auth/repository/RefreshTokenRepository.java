package com.gakkaweo.backend.domain.auth.repository;

import com.gakkaweo.backend.domain.auth.entity.RefreshToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  List<RefreshToken> findByFamilyId(UUID familyId);

  List<RefreshToken> findByMemberId(Long memberId);

  @Modifying
  @Query("DELETE FROM RefreshToken r WHERE r.member.id = :memberId")
  int deleteByMemberId(@Param("memberId") Long memberId);
}
