package com.gakkaweo.backend.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.admin.dto.AnnouncementCreateRequest;
import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.game.dto.GuessRequest;
import com.gakkaweo.backend.game.dto.GuessResponse;
import com.gakkaweo.backend.ranking.sse.SseConnectionManager;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

@DisplayName("SSE 이벤트/연결 통합 테스트")
class SseEventTest extends IntegrationTestBase {

  @Autowired SseConnectionManager sseConnectionManager;
  @Autowired Clock clock;

  @Test
  @DisplayName("추측 제출 → RankingUpdateEvent 발행")
  void RankingUpdateEvent_발행() {
    Member member = testAuthHelper.createMember();
    DailySentence sentence = testAuthHelper.createTodaySentence("안녕하세요");
    testSimilarityClient.setDefaultScore(new BigDecimal("60.0"));

    HttpHeaders headers = testAuthHelper.cookieHeaderFor(member);
    headers.setContentType(MediaType.APPLICATION_JSON);

    restTemplate.exchange(
        url("/daily/guess"),
        HttpMethod.POST,
        new HttpEntity<>(new GuessRequest(sentence.getPublicId(), "안녕"), headers),
        GuessResponse.class);

    Awaitility.await()
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> assertThat(testEventCollector.getRankingEvents()).isNotEmpty());
  }

  @Test
  @DisplayName("공지 등록 → AnnouncementEvent 발행 (활성 기간 내)")
  void AnnouncementEvent_발행() {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);
    headers.setContentType(MediaType.APPLICATION_JSON);

    AnnouncementCreateRequest request =
        new AnnouncementCreateRequest(
            "점검 공지",
            "내용",
            "MAINTENANCE",
            clock.instant().minusSeconds(60),
            clock.instant().plus(Duration.ofDays(1)));

    restTemplate.exchange(
        url("/admin/system/announcements"),
        HttpMethod.POST,
        new HttpEntity<>(request, headers),
        Void.class);

    Awaitility.await()
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> assertThat(testEventCollector.getAnnouncementEvents()).isNotEmpty());
  }

  @Test
  @DisplayName("SSE 연결 수 - 초기 0")
  void 초기_연결수() {
    assertThat(sseConnectionManager.getConnectionCount()).isEqualTo(0);
  }
}
