package com.gakkaweo.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.admin.dto.AnnouncementCreateRequest;
import com.gakkaweo.backend.admin.dto.AnnouncementResponse;
import com.gakkaweo.backend.admin.dto.AuditLogListResponse;
import com.gakkaweo.backend.admin.dto.SystemStatusResponse;
import com.gakkaweo.backend.common.exception.ErrorBody;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@DisplayName("Admin 시스템 관리 통합 테스트")
class AdminSystemIntegrationTest extends IntegrationTestBase {

  @Autowired Clock clock;

  @Test
  @DisplayName("공지 등록 - 201 + 목록 조회")
  void 공지_CRUD() {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = authedJson(admin);

    AnnouncementCreateRequest request =
        new AnnouncementCreateRequest(
            "테스트 공지", "내용", "INFO", clock.instant(), clock.instant().plus(Duration.ofDays(1)));

    ResponseEntity<AnnouncementResponse> created =
        restTemplate.exchange(
            url("/admin/system/announcements"),
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            AnnouncementResponse.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().title()).isEqualTo("테스트 공지");
    assertThat(created.getBody().type()).isEqualTo("INFO");
  }

  @Test
  @DisplayName("공지 수정 - 존재하지 않는 ID 404 ANNOUNCEMENT_NOT_FOUND")
  void 공지없음_404() {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = authedJson(admin);

    String body = "{\"title\":\"바뀐 제목\"}";

    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/admin/system/announcements/99999"),
            HttpMethod.PATCH,
            new HttpEntity<>(body, headers),
            ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().code()).isEqualTo("ANNOUNCEMENT_NOT_FOUND");
  }

  @Test
  @DisplayName("시스템 상태 조회 - sseConnectionCount 등 반환")
  void 시스템상태() {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    ResponseEntity<SystemStatusResponse> response =
        restTemplate.exchange(
            url("/admin/system/status"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            SystemStatusResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().sseConnectionCount()).isGreaterThanOrEqualTo(0);
    assertThat(response.getBody().redisHealthy()).isTrue();
  }

  @Test
  @DisplayName("랭킹 캐시 리셋 - 200")
  void 랭킹캐시_리셋() {
    testAuthHelper.createTodaySentence("오늘 문장");
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    ResponseEntity<Void> response =
        restTemplate.exchange(
            url("/admin/system/ranking-cache/reset"),
            HttpMethod.POST,
            new HttpEntity<>(headers),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("Rate Limit 리셋 - 200")
  void 레이트리밋_리셋() {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    ResponseEntity<Void> response =
        restTemplate.exchange(
            url("/admin/system/rate-limit/reset"),
            HttpMethod.POST,
            new HttpEntity<>(headers),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("감사 로그 조회 - 페이지네이션")
  void 감사로그_조회() {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    ResponseEntity<AuditLogListResponse> response =
        restTemplate.exchange(
            url("/admin/system/audit-logs?page=0&size=10"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            AuditLogListResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().content()).isNotNull();
  }

  private HttpHeaders authedJson(Member admin) {
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }
}
