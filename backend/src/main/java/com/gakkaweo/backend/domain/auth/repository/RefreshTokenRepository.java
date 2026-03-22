package com.gakkaweo.backend.domain.auth.repository;

import com.gakkaweo.backend.domain.auth.entity.RefreshToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  List<RefreshToken> findByFamilyId(UUID familyId);

  List<RefreshToken> findByMemberId(Long memberId);

  void deleteByMemberId(Long memberId);
}
