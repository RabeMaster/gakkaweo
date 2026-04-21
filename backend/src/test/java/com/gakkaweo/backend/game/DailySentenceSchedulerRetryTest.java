package com.gakkaweo.backend.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.DailySentenceStatus;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.game.scheduler.DailySentenceScheduler;
import com.gakkaweo.backend.infra.notification.NotificationLevel;
import com.gakkaweo.backend.infra.notification.client.DiscordWebhookClient;
import com.gakkaweo.backend.infra.notification.dto.DiscordEmbed;
import com.gakkaweo.backend.ranking.dto.RankingSnapshot;
import com.gakkaweo.backend.ranking.service.RankingService;
import com.gakkaweo.backend.support.IntegrationTestBase;
import com.gakkaweo.backend.support.TestClock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("DailySentenceScheduler 재시도 및 Discord 전송 통합 테스트")
class DailySentenceSchedulerRetryTest extends IntegrationTestBase {

  @Autowired DailySentenceScheduler scheduler;
  @Autowired DailySentenceRepository dailySentenceRepository;
  @Autowired TransactionTemplate transactionTemplate;

  @MockitoBean RankingService rankingService;
  @MockitoBean DiscordWebhookClient discordWebhookClient;

  @BeforeEach
  void resetMockInvocations() {
    Mockito.clearInvocations(rankingService, discordWebhookClient);
  }

  @Test
  @DisplayName("getAllRankingsForDate 첫 호출 실패 → 재시도 후 성공하면 총 2번 호출")
  void 스냅샷_단계_재시도_후_성공() {
    TestClock testClock = (TestClock) clock;
    LocalDate yesterday = LocalDate.now(testClock);

    transactionTemplate.executeWithoutResult(
        status -> {
          DailySentence s = new DailySentence("어제 문장");
          s.setUsedAt(yesterday);
          s.setStatus(DailySentenceStatus.USED);
          dailySentenceRepository.save(s);
        });
    testAuthHelper.createActiveSentence("오늘 후보");
    testClock.advanceBy(Duration.ofDays(1));

    when(rankingService.getAllRankingsForDate(yesterday))
        .thenThrow(new RuntimeException("일시적 오류"))
        .thenReturn(new RankingSnapshot(List.of(), 0));

    scheduler.executeMidnightJob();

    verify(rankingService, times(2)).getAllRankingsForDate(yesterday);
  }

  @Test
  @DisplayName("getAllRankingsForDate 3회 모두 실패 시 스냅샷 단계 최종 실패로 embed 경고 전송")
  void 스냅샷_단계_최대_재시도_초과() {
    TestClock testClock = (TestClock) clock;
    LocalDate yesterday = LocalDate.now(testClock);

    transactionTemplate.executeWithoutResult(
        status -> {
          DailySentence s = new DailySentence("어제 문장");
          s.setUsedAt(yesterday);
          s.setStatus(DailySentenceStatus.USED);
          dailySentenceRepository.save(s);
        });
    testAuthHelper.createActiveSentence("오늘 후보");
    testClock.advanceBy(Duration.ofDays(1));

    when(rankingService.getAllRankingsForDate(yesterday)).thenThrow(new RuntimeException("영구 오류"));

    scheduler.executeMidnightJob();

    verify(rankingService, times(3)).getAllRankingsForDate(yesterday);

    ArgumentCaptor<DiscordEmbed> captor = ArgumentCaptor.forClass(DiscordEmbed.class);
    verify(discordWebhookClient).send(eq(NotificationLevel.INFO), captor.capture());
    DiscordEmbed embed = captor.getValue();
    assertThat(embed.title()).contains("일부 실패");
    assertThat(embed.fields()).anyMatch(f -> f.name().equals("랭킹 스냅샷") && f.value().equals("실패"));
  }

  @Test
  @DisplayName("executeMidnightJob 완료 시 Discord embed에 스포일러 문장 포함 전송")
  void Discord_embed_스포일러_전송() {
    when(rankingService.getAllRankingsForDate(any())).thenReturn(new RankingSnapshot(List.of(), 0));

    testAuthHelper.createActiveSentence("오늘 테스트 문장");

    scheduler.executeMidnightJob();

    ArgumentCaptor<DiscordEmbed> captor = ArgumentCaptor.forClass(DiscordEmbed.class);
    verify(discordWebhookClient).send(eq(NotificationLevel.INFO), captor.capture());
    DiscordEmbed embed = captor.getValue();
    assertThat(embed.title()).contains("실행 완료");
    assertThat(embed.description()).contains("||오늘 테스트 문장||");
    assertThat(embed.fields())
        .extracting(DiscordEmbed.Field::name)
        .contains("어제 세션 만료", "랭킹 스냅샷", "랭킹 키 만료", "오늘 문장 선정", "어제 참여자 수", "미사용 문장 잔여");
  }
}
