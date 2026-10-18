package com.gakkaweo.backend.ranking;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.ranking.service.RankingService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

@DisplayName("영구 랭킹 스냅샷 실패 보존 단위 테스트")
class RankingSnapshotFailureTest {

  @Test
  @SuppressWarnings("unchecked")
  @DisplayName("Redis 조회 실패를 빈 랭킹으로 숨기지 않아 자정 스케줄러가 재시도할 수 있음")
  void Redis_실패_전파() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ZSetOperations<String, String> zset = mock(ZSetOperations.class);
    when(redis.opsForZSet()).thenReturn(zset);
    when(zset.zCard(anyString())).thenThrow(new QueryTimeoutException("synthetic timeout"));
    RankingService service =
        new RankingService(
            redis,
            mock(DailySentenceRepository.class),
            mock(GameSessionRepository.class),
            new SimpleMeterRegistry(),
            Clock.systemUTC(),
            new com.gakkaweo.backend.ranking.service.RankingWriteCoordinator());

    assertThatThrownBy(() -> service.getAllRankingsForDate(LocalDate.of(2026, 1, 1)))
        .isInstanceOf(QueryTimeoutException.class);
  }
}
