package com.gakkaweo.backend.announcement;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.domain.admin.entity.Announcement;
import com.gakkaweo.backend.domain.admin.entity.AnnouncementType;
import com.gakkaweo.backend.domain.admin.repository.AnnouncementRepository;
import com.gakkaweo.backend.support.IntegrationTestBase;
import com.gakkaweo.backend.support.TestClock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("공지 공개 엔드포인트 통합 테스트")
class AnnouncementPublicEndpointTest extends IntegrationTestBase {

  private static final ParameterizedTypeReference<List<ActiveAnnouncementResponse>> RESPONSE_TYPE =
      new ParameterizedTypeReference<>() {};
  @Autowired AnnouncementRepository announcementRepository;
  @Autowired TransactionTemplate transactionTemplate;

  @Test
  @DisplayName("미인증 호출 - 200 + 활성 공지 반환")
  void 미인증_200() {
    Instant now = clock.instant();
    saveAnnouncement("활성 공지", AnnouncementType.INFO, true, now.minusSeconds(60), null);

    ResponseEntity<List<ActiveAnnouncementResponse>> response =
        restTemplate.exchange(url("/announcements/active"), HttpMethod.GET, null, RESPONSE_TYPE);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).hasSize(1);
    ActiveAnnouncementResponse body = response.getBody().get(0);
    assertThat(body.title()).isEqualTo("활성 공지");
    assertThat(body.type()).isEqualTo("INFO");
    assertThat(body.content()).isNull();
  }

  @Test
  @DisplayName("시작 시각 미래 - 제외")
  void 시작시각_미래_제외() {
    Instant now = clock.instant();
    saveAnnouncement("미래 공지", AnnouncementType.INFO, true, now.plusSeconds(3600), null);

    List<ActiveAnnouncementResponse> body = fetchActive();

    assertThat(body).isEmpty();
  }

  @Test
  @DisplayName("종료 시각 과거 - 제외")
  void 종료시각_과거_제외() {
    Instant now = clock.instant();
    saveAnnouncement(
        "만료 공지",
        AnnouncementType.WARNING,
        true,
        now.minus(Duration.ofDays(2)),
        now.minusSeconds(60));

    List<ActiveAnnouncementResponse> body = fetchActive();

    assertThat(body).isEmpty();
  }

  @Test
  @DisplayName("endsAt null - 포함")
  void endsAt_null_포함() {
    Instant now = clock.instant();
    saveAnnouncement("무기한 공지", AnnouncementType.MAINTENANCE, true, now.minusSeconds(60), null);

    List<ActiveAnnouncementResponse> body = fetchActive();

    assertThat(body).hasSize(1);
    assertThat(body.get(0).type()).isEqualTo("MAINTENANCE");
    assertThat(body.get(0).endsAt()).isNull();
  }

  @Test
  @DisplayName("active=false - 제외")
  void 비활성_제외() {
    Instant now = clock.instant();
    saveAnnouncement("꺼진 공지", AnnouncementType.INFO, false, now.minusSeconds(60), null);

    List<ActiveAnnouncementResponse> body = fetchActive();

    assertThat(body).isEmpty();
  }

  @Test
  @DisplayName("여러 공지 - createdAt DESC 정렬")
  void 정렬_검증() {
    Instant now = clock.instant();
    saveAnnouncement("먼저", AnnouncementType.INFO, true, now.minusSeconds(120), null);
    if (clock instanceof TestClock testClock) {
      testClock.advanceBy(Duration.ofSeconds(1));
    }
    saveAnnouncement("나중", AnnouncementType.INFO, true, now.minusSeconds(60), null);

    List<ActiveAnnouncementResponse> body = fetchActive();

    assertThat(body).extracting(ActiveAnnouncementResponse::title).containsExactly("나중", "먼저");
  }

  private List<ActiveAnnouncementResponse> fetchActive() {
    return restTemplate
        .exchange(url("/announcements/active"), HttpMethod.GET, null, RESPONSE_TYPE)
        .getBody();
  }

  private void saveAnnouncement(
      String title, AnnouncementType type, boolean active, Instant startsAt, Instant endsAt) {
    transactionTemplate.executeWithoutResult(
        status -> {
          Announcement announcement = new Announcement(null, title, null, type, startsAt, endsAt);
          announcement.setActive(active);
          announcementRepository.save(announcement);
        });
  }
}
