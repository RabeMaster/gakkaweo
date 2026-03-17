package com.gakkaweo.backend.auth.service;

import com.gakkaweo.backend.auth.config.JwtProperties;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.auth.entity.RefreshToken;
import com.gakkaweo.backend.domain.auth.repository.RefreshTokenRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class RefreshTokenService {

  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtProperties jwtProperties;

  public RefreshTokenService(
      RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.jwtProperties = jwtProperties;
  }

  @Transactional
  public String createRefreshToken(Member member) {
    String rawToken = UUID.randomUUID().toString();
    String tokenHash = hashToken(rawToken);
    UUID familyId = UUID.randomUUID();
    LocalDateTime expiresAt = LocalDateTime.now().plus(jwtProperties.getRefreshExpiration());

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
      revokeFamily(existing.getFamilyId());
      throw new BusinessException(ErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
    }

    if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
      log.warn("만료된 Refresh Token 사용 시도: familyId={}", existing.getFamilyId());
      existing.setRevoked(true);
      throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
    }

    existing.setRevoked(true);

    String newRawToken = UUID.randomUUID().toString();
    String newTokenHash = hashToken(newRawToken);
    LocalDateTime expiresAt = LocalDateTime.now().plus(jwtProperties.getRefreshExpiration());

    RefreshToken newRefreshToken =
        new RefreshToken(existing.getMember(), newTokenHash, existing.getFamilyId(), expiresAt);
    refreshTokenRepository.save(newRefreshToken);

    return newRawToken;
  }

  @Transactional
  public void revokeFamily(UUID familyId) {
    List<RefreshToken> tokens = refreshTokenRepository.findByFamilyId(familyId);
    tokens.forEach(token -> token.setRevoked(true));
    log.info("Token Family 폐기: familyId={}, count={}", familyId, tokens.size());
  }

  @Transactional(readOnly = true)
  public RefreshToken findByRawToken(String rawToken) {
    String tokenHash = hashToken(rawToken);
    return refreshTokenRepository
        .findByTokenHash(tokenHash)
        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
  }

  public String hashToken(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다", e);
    }
  }
}
