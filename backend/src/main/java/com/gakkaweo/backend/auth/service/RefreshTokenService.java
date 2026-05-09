package com.gakkaweo.backend.auth.service;

import com.gakkaweo.backend.auth.config.JwtProperties;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.common.util.HashUtils;
import com.gakkaweo.backend.domain.auth.entity.RefreshToken;
import com.gakkaweo.backend.domain.auth.repository.RefreshTokenRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenService {

  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtProperties jwtProperties;
  private final Clock clock;
  private final TransactionTemplate newTxTemplate;

  @Transactional
  public String createRefreshToken(Member member) {
    String rawToken = UUID.randomUUID().toString();
    String tokenHash = hashToken(rawToken);
    UUID familyId = UUID.randomUUID();
    Instant expiresAt = clock.instant().plus(jwtProperties.refreshExpiration());

    RefreshToken refreshToken = new RefreshToken(member, tokenHash, familyId, expiresAt);
    refreshTokenRepository.save(refreshToken);

    return rawToken;
  }

  @Transactional
  public String rotateRefreshToken(String rawToken) {
    String tokenHash = hashToken(rawToken);
    RefreshToken existing =
        refreshTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

    if (existing.isRevoked()) {
      log.warn("Refresh Token 재사용 감지: familyId={}", existing.getFamilyId());
      UUID familyId = existing.getFamilyId();
      // 상위 TX 롤백과 무관하게 family revoke를 즉시 commit (REQUIRES_NEW)
      newTxTemplate.executeWithoutResult(status -> revokeFamilyInternal(familyId));
      throw new BusinessException(ErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
    }

    if (existing.getExpiresAt().isBefore(clock.instant())) {
      log.warn("만료된 Refresh Token 사용 시도: familyId={}", existing.getFamilyId());
      existing.setRevoked(true);
      throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
    }

    existing.setRevoked(true);

    String newRawToken = UUID.randomUUID().toString();
    String newTokenHash = hashToken(newRawToken);
    Instant expiresAt = clock.instant().plus(jwtProperties.refreshExpiration());

    RefreshToken newRefreshToken =
        new RefreshToken(existing.getMember(), newTokenHash, existing.getFamilyId(), expiresAt);
    refreshTokenRepository.save(newRefreshToken);

    return newRawToken;
  }

  @Transactional(readOnly = true)
  public RefreshToken findByRawToken(String rawToken) {
    String tokenHash = hashToken(rawToken);
    return refreshTokenRepository
        .findByTokenHash(tokenHash)
        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
  }

  public String hashToken(String rawToken) {
    return HashUtils.sha256Hex(rawToken);
  }

  private void revokeFamilyInternal(UUID familyId) {
    List<RefreshToken> tokens = refreshTokenRepository.findByFamilyId(familyId);
    tokens.forEach(token -> token.setRevoked(true));
    refreshTokenRepository.saveAll(tokens);
    log.info("Token Family 폐기: familyId={}, count={}", familyId, tokens.size());
  }
}
