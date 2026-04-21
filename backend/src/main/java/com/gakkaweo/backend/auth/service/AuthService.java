package com.gakkaweo.backend.auth.service;

import static com.gakkaweo.backend.common.redis.RedisKeyConstants.BLACKLIST_PREFIX;
import static com.gakkaweo.backend.common.redis.RedisKeyConstants.RANKING_DETAIL_PREFIX;
import static com.gakkaweo.backend.common.redis.RedisKeyConstants.RANKING_MEMBER_PREFIX;

import com.gakkaweo.backend.auth.dto.AuthResponse;
import com.gakkaweo.backend.auth.dto.TokenPair;
import com.gakkaweo.backend.auth.jwt.JwtProvider;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.auth.entity.RefreshToken;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.domain.member.validation.NicknameValidator;
import com.gakkaweo.backend.ranking.event.RankingUpdateEvent;
import io.jsonwebtoken.Claims;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

  private final JwtProvider jwtProvider;
  private final RefreshTokenService refreshTokenService;
  private final MemberRepository memberRepository;
  private final NicknameValidator nicknameValidator;
  private final StringRedisTemplate redisTemplate;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Transactional
  public TokenPair issueTokens(Member member) {
    String accessToken =
        jwtProvider.createAccessToken(member.getPublicId(), member.getRole().name());
    String refreshToken = refreshTokenService.createRefreshToken(member);
    log.info("토큰 발급: memberId={}", member.getPublicId());
    return new TokenPair(accessToken, refreshToken);
  }

  @Transactional
  public TokenPair refresh(String rawRefreshToken) {
    RefreshToken existing = refreshTokenService.findByRawToken(rawRefreshToken);
    String newRawRefreshToken = refreshTokenService.rotateRefreshToken(rawRefreshToken);

    Member member = existing.getMember();
    if (Boolean.TRUE.equals(member.getBanned())) {
      throw new BusinessException(ErrorCode.MEMBER_BANNED);
    }

    String accessToken =
        jwtProvider.createAccessToken(member.getPublicId(), member.getRole().name());

    log.info("토큰 갱신: memberId={}", member.getPublicId());
    return new TokenPair(accessToken, newRawRefreshToken);
  }

  public void logout(String accessToken) {
    Claims claims = jwtProvider.parseAccessToken(accessToken);
    String jti = claims.getId();
    long remainingMillis = claims.getExpiration().getTime() - clock.millis();

    if (remainingMillis > 0) {
      redisTemplate
          .opsForValue()
          .set(BLACKLIST_PREFIX + jti, "logout", Duration.ofMillis(remainingMillis));
    }
    log.info("로그아웃: jti={}", jti);
  }

  @Transactional(readOnly = true)
  public AuthResponse getCurrentUser(UUID publicId) {
    Member member =
        memberRepository
            .findByPublicId(publicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

    return AuthResponse.from(member);
  }

  @Transactional
  public AuthResponse changeNickname(UUID publicId, String rawNickname) {
    String nickname = nicknameValidator.normalize(rawNickname);
    nicknameValidator.validate(nickname);

    Member member =
        memberRepository
            .findByPublicId(publicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

    if (member.getNickname().equals(nickname)) {
      throw new BusinessException(ErrorCode.NICKNAME_UNCHANGED);
    }

    if (memberRepository.existsByNickname(nickname)) {
      throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
    }

    member.setNickname(nickname);

    try {
      memberRepository.flush();
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
    }

    return AuthResponse.from(member);
  }

  @Transactional
  public AuthResponse updateProfileUrl(UUID publicId, String profileUrl) {
    Member member =
        memberRepository
            .findByPublicId(publicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

    member.setProfileUrl(profileUrl);

    return AuthResponse.from(member);
  }

  public void syncProfileUrlToRedis(UUID publicId, String profileUrl) {
    try {
      LocalDate today = LocalDate.now(clock);
      String detailKey = RANKING_DETAIL_PREFIX + today + ":" + RANKING_MEMBER_PREFIX + publicId;

      if (redisTemplate.hasKey(detailKey)) {
        redisTemplate.opsForHash().put(detailKey, "profileUrl", Objects.toString(profileUrl, ""));
        eventPublisher.publishEvent(new RankingUpdateEvent());
      }
    } catch (Exception e) {
      log.warn("프로필 URL Redis 동기화 실패: publicId={}", publicId, e);
    }
  }

  public void syncNicknameToRedis(UUID publicId, String nickname) {
    try {
      LocalDate today = LocalDate.now(clock);
      String detailKey = RANKING_DETAIL_PREFIX + today + ":" + RANKING_MEMBER_PREFIX + publicId;

      if (redisTemplate.hasKey(detailKey)) {
        redisTemplate.opsForHash().put(detailKey, "nickname", nickname);
        eventPublisher.publishEvent(new RankingUpdateEvent());
      }
    } catch (Exception e) {
      log.warn("닉네임 Redis 동기화 실패: publicId={}", publicId, e);
    }
  }
}
