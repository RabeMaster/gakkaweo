package com.gakkaweo.backend.auth.service;

import com.gakkaweo.backend.auth.metrics.AuthMetrics;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.admin.repository.SentenceUploadRepository;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.LocalAccountRepository;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.domain.member.repository.SocialAccountRepository;
import com.gakkaweo.backend.ranking.event.RankingUpdateEvent;
import com.gakkaweo.backend.ranking.service.RankingService;
import com.gakkaweo.backend.ranking.service.RankingWriteCoordinator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

  private final TransactionTemplate transactionTemplate;
  private final RankingWriteCoordinator rankingWriteCoordinator;
  private final MemberRepository memberRepository;
  private final GameSessionRepository gameSessionRepository;
  private final SentenceUploadRepository sentenceUploadRepository;
  private final LocalAccountRepository localAccountRepository;
  private final SocialAccountRepository socialAccountRepository;
  private final AuthSessionService authSessionService;
  private final RankingService rankingService;
  private final ApplicationEventPublisher eventPublisher;
  private final AuthMetrics authMetrics;

  public void deleteAccount(UUID publicId) {
    rankingWriteCoordinator.write(
        transactionTemplate,
        status -> {
          Member member =
              memberRepository
                  .findByPublicIdForUpdate(publicId)
                  .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

          int anonymizedSessions = gameSessionRepository.anonymizeByMember(member);
          int anonymizedUploads = sentenceUploadRepository.anonymizeByAdmin(member);

          localAccountRepository.deleteByMember(member);
          socialAccountRepository.deleteByMember(member);
          authSessionService.deleteAll(member.getId());
          memberRepository.deleteById(member.getId());

          authMetrics.recordWithdraw();
          log.info(
              "회원 탈퇴 완료: publicId={}, anonymizedSessions={}, anonymizedUploads={}",
              publicId,
              anonymizedSessions,
              anonymizedUploads);

          return null;
        });
  }

  public void cleanupRedis(UUID publicId) {
    if (rankingService.cleanupMemberRanking(publicId)) {
      eventPublisher.publishEvent(new RankingUpdateEvent());
    }

    log.info("탈퇴 회원 Redis 정리 완료: publicId={}", publicId);
  }
}
