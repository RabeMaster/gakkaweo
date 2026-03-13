package com.gakkaweo.backend.auth.service;

import com.gakkaweo.backend.auth.dto.AuthResponse;
import com.gakkaweo.backend.auth.dto.TokenPair;
import com.gakkaweo.backend.auth.entity.RefreshToken;
import com.gakkaweo.backend.auth.jwt.JwtProvider;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final JwtProvider jwtProvider;
  private final RefreshTokenService refreshTokenService;
  private final MemberRepository memberRepository;
  private final StringRedisTemplate redisTemplate;

  public AuthService(
      JwtProvider jwtProvider,
      RefreshTokenService refreshTokenService,
      MemberRepository memberRepository,
      StringRedisTemplate redisTemplate) {
    this.jwtProvider = jwtProvider;
    this.refreshTokenService = refreshTokenService;
    this.memberRepository = memberRepository;
    this.redisTemplate = redisTemplate;
  }

  @Transactional
  public TokenPair issueTokens(Member member) {
    String accessToken =
        jwtProvider.createAccessToken(member.getPublicId(), member.getRole().name());
    String refreshToken = refreshTokenService.createRefreshToken(member);
    return new TokenPair(accessToken, refreshToken);
  }

  @Transactional
  public TokenPair refresh(String rawRefreshToken) {
    RefreshToken existing = refreshTokenService.findByRawToken(rawRefreshToken);
    String newRawRefreshToken = refreshTokenService.rotateRefreshToken(rawRefreshToken);

    Member member = existing.getMember();
    String accessToken =
        jwtProvider.createAccessToken(member.getPublicId(), member.getRole().name());

    return new TokenPair(accessToken, newRawRefreshToken);
  }

  public void logout(String accessToken) {
    Claims claims = jwtProvider.parseAccessToken(accessToken);
    String jti = claims.getId();
    long remainingMillis = claims.getExpiration().getTime() - System.currentTimeMillis();

    if (remainingMillis > 0) {
      redisTemplate
          .opsForValue()
          .set("blacklist:jti:" + jti, "logout", Duration.ofMillis(remainingMillis));
    }
  }

  @Transactional(readOnly = true)
  public AuthResponse getCurrentUser(UUID publicId) {
    Member member =
        memberRepository
            .findByPublicId(publicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

    return new AuthResponse(
        member.getPublicId(),
        member.getNickname(),
        member.getProfileUrl(),
        member.getRole().name());
  }
}
