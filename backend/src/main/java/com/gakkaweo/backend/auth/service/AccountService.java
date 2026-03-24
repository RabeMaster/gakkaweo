package com.gakkaweo.backend.auth.service;

import static com.gakkaweo.backend.common.redis.RedisKeyConstants.RANKING_DETAIL_PREFIX;
import static com.gakkaweo.backend.common.redis.RedisKeyConstants.RANKING_KEY_PREFIX;
import static com.gakkaweo.backend.common.redis.RedisKeyConstants.RANKING_MEMBER_PREFIX;

import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.admin.repository.SentenceUploadRepository;
import com.gakkaweo.backend.domain.auth.repository.RefreshTokenRepository;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.domain.member.repository.SocialAccountRepository;
import com.gakkaweo.backend.ranking.event.RankingUpdateEvent;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final MemberRepository memberRepository;
  private final GameSessionRepository gameSessionRepository;
  private final SentenceUploadRepository sentenceUploadRepository;
  private final SocialAccountRepository socialAccountRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final AuthService authService;
  private final StringRedisTemplate redisTemplate;
  private final ApplicationEventPublisher eventPublisher;

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
      String memberKey = RANKING_MEMBER_PREFIX + publicId;
      String detailKey = RANKING_DETAIL_PREFIX + today + ":" + RANKING_MEMBER_PREFIX + publicId;

      Long removed = redisTemplate.opsForZSet().remove(rankingKey, memberKey);
      redisTemplate.delete(detailKey);

      if (removed != null && removed > 0) {
        eventPublisher.publishEvent(new RankingUpdateEvent());
      }

      log.info("탈퇴 회원 Redis 정리 완료: publicId={}", publicId);
    } catch (Exception e) {
      log.warn("탈퇴 회원 Redis 정리 실패: publicId={}", publicId, e);
    }
  }
}
