package com.gakkaweo.backend.auth.service;

import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.admin.repository.SentenceUploadRepository;
import com.gakkaweo.backend.domain.auth.repository.RefreshTokenRepository;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.LocalAccountRepository;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.domain.member.repository.SocialAccountRepository;
import com.gakkaweo.backend.ranking.event.RankingUpdateEvent;
import com.gakkaweo.backend.ranking.service.RankingService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

  private final MemberRepository memberRepository;
  private final GameSessionRepository gameSessionRepository;
  private final SentenceUploadRepository sentenceUploadRepository;
  private final LocalAccountRepository localAccountRepository;
  private final SocialAccountRepository socialAccountRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final AuthService authService;
  private final RankingService rankingService;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public void deleteAccount(UUID publicId) {
    Member member =
        memberRepository
            .findByPublicId(publicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

    int anonymizedSessions = gameSessionRepository.anonymizeByMember(member);
    int anonymizedUploads = sentenceUploadRepository.anonymizeByAdmin(member);

    localAccountRepository.deleteByMember(member);
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
    if (accessToken != null) {
      authService.logout(accessToken);
    }

    if (rankingService.cleanupMemberRanking(publicId)) {
      eventPublisher.publishEvent(new RankingUpdateEvent());
    }

    log.info("탈퇴 회원 Redis 정리 완료: publicId={}", publicId);
  }
}
