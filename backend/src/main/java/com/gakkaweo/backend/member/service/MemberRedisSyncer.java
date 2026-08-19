package com.gakkaweo.backend.member.service;

import com.gakkaweo.backend.common.redis.RedisKeyConstants;
import com.gakkaweo.backend.ranking.event.RankingUpdateEvent;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MemberRedisSyncer {

  private final StringRedisTemplate redisTemplate;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public void updateProfileUrl(UUID publicId, String profileUrl) {
    updateRankingDetailField(publicId, "profileUrl", Objects.toString(profileUrl, ""));
  }

  public void updateNickname(UUID publicId, String nickname) {
    updateRankingDetailField(publicId, "nickname", nickname);
  }

  private void updateRankingDetailField(UUID publicId, String field, String value) {
    try {
      LocalDate today = LocalDate.now(clock);
      String detailKey = RedisKeyConstants.rankingDetailKey(today, publicId);
      if (redisTemplate.hasKey(detailKey)) {
        redisTemplate.opsForHash().put(detailKey, field, value);
        eventPublisher.publishEvent(new RankingUpdateEvent());
      }
    } catch (Exception e) {
      log.warn("랭킹 detail Redis 동기화 실패: publicId={}, field={}", publicId, field, e);
    }
  }
}
