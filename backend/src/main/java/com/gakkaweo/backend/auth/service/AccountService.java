package com.gakkaweo.backend.auth.service;

import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.admin.repository.SentenceUploadRepository;
import com.gakkaweo.backend.domain.auth.repository.RefreshTokenRepository;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.domain.member.repository.SocialAccountRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AccountService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final String RANKING_KEY_PREFIX = "ranking:";
  private static final String DETAIL_KEY_PREFIX = "ranking_detail:";
  private static final String MEMBER_PREFIX = "member:";

  private final MemberRepository memberRepository;
  private final GameSessionRepository gameSessionRepository;
  private final SentenceUploadRepository sentenceUploadRepository;
  private final SocialAccountRepository socialAccountRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final AuthService authService;
  private final StringRedisTemplate redisTemplate;

  public AccountService(
      MemberRepository memberRepository,
      GameSessionRepository gameSessionRepository,
      SentenceUploadRepository sentenceUploadRepository,
      SocialAccountRepository socialAccountRepository,
      RefreshTokenRepository refreshTokenRepository,
      AuthService authService,
      StringRedisTemplate redisTemplate) {
    this.memberRepository = memberRepository;
    this.gameSessionRepository = gameSessionRepository;
    this.sentenceUploadRepository = sentenceUploadRepository;
    this.socialAccountRepository = socialAccountRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.authService = authService;
    this.redisTemplate = redisTemplate;
  }

  @Transactional
  public void deleteAccount(UUID publicId) {
    Member member =
        memberRepository
            .findByPublicId(publicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

    int anonymizedSessions = gameSessionRepository.anonymizeByMember(member);
    int anonymizedUploads = sentenceUploadRepository.anonymizeByAdmin(member);

    socialAccountRepository.deleteByMember(member);
    refreshTokenRepository.deleteByMemberId(member.getId());
    memberRepository.delete(member);

    log.info(
        "회원 탈퇴 완료: publicId={}, anonymizedSessions={}, anonymizedUploads={}",
        publicId,
        anonymizedSessions,
        anonymizedUploads);
  }

  public void cleanupRedis(UUID publicId, String accessToken) {
    try {
      if (accessToken != null) {
        authService.logout(accessToken);
      }

      LocalDate today = LocalDate.now(KST);
      String rankingKey = RANKING_KEY_PREFIX + today;
      String memberKey = MEMBER_PREFIX + publicId;
      String detailKey = DETAIL_KEY_PREFIX + today + ":" + MEMBER_PREFIX + publicId;

      redisTemplate.opsForZSet().remove(rankingKey, memberKey);
      redisTemplate.delete(detailKey);

      log.info("탈퇴 회원 Redis 정리 완료: publicId={}", publicId);
    } catch (Exception e) {
      log.warn("탈퇴 회원 Redis 정리 실패: publicId={}, {}", publicId, e.getMessage());
    }
  }
}
