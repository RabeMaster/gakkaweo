package com.gakkaweo.backend.member.service;

import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.ranking.event.RankingUpdateEvent;
import com.gakkaweo.backend.ranking.service.RankingService;
import com.gakkaweo.backend.ranking.service.RankingWriteCoordinator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MemberRedisSyncer {

  private final MemberRepository memberRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final RankingWriteCoordinator rankingWriteCoordinator;
  private final RankingService rankingService;
  private final Clock clock;

  public void updateProfileUrl(UUID publicId, String profileUrl) {
    sync(publicId);
  }

  public void updateNickname(UUID publicId, String nickname) {
    sync(publicId);
  }

  private void sync(UUID publicId) {
    rankingWriteCoordinator.lock();
    try {
      // 호출자가 전달한 이전 값 대신 최신 DB 상태로 전체 랭킹을 복원합니다.
      rankingWriteCoordinator.changed();
      rankingService.markRecoveryNeeded(LocalDate.now(clock));
      if (memberRepository.findByPublicId(publicId).isEmpty()
          || rankingService.isCacheCircuitOpen()) {
        return;
      }
    } finally {
      rankingWriteCoordinator.unlock();
    }
    try {
      rankingService.rebuildRankingCache(LocalDate.now(clock));
      eventPublisher.publishEvent(new RankingUpdateEvent());
    } catch (RuntimeException e) {
      log.warn("회원 정보의 랭킹 반영을 다음 복구 주기에 재시도합니다", e);
    }
  }
}
