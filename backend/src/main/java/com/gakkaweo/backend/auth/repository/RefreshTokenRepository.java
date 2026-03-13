package com.gakkaweo.backend.auth.repository;

import com.gakkaweo.backend.auth.entity.RefreshToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  List<RefreshToken> findByFamilyId(UUID familyId);

  List<RefreshToken> findByMemberId(Long memberId);
}
