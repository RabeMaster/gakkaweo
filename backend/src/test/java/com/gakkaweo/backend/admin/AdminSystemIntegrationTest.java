package com.gakkaweo.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.admin.dto.AnnouncementCreateRequest;
import com.gakkaweo.backend.admin.dto.AnnouncementResponse;
import com.gakkaweo.backend.admin.dto.AuditLogListResponse;
import com.gakkaweo.backend.admin.dto.SystemStatusResponse;
import com.gakkaweo.backend.admin.event.AnnouncementEvent;
import com.gakkaweo.backend.common.exception.ErrorBody;
import com.gakkaweo.backend.domain.admin.entity.AuditLog;
import com.gakkaweo.backend.domain.admin.repository.AuditLogRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("Admin 시스템 관리 통합 테스트")
class AdminSystemIntegrationTest extends IntegrationTestBase {

  @Autowired Clock clock;
  @Autowired AuditLogRepository auditLogRepository;
  @Autowired TransactionTemplate transactionTemplate;

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

  @Test
  @DisplayName("공지 등록 - 활성 기간(현재 포함)이면 AnnouncementEvent 발행")
  void 공지등록_활성기간_이벤트발행() {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = authedJson(admin);
    Instant now = clock.instant();

    AnnouncementCreateRequest request =
        new AnnouncementCreateRequest(
            "진행중", "내용", "MAINTENANCE", now.minusSeconds(60), now.plus(Duration.ofHours(1)));

    ResponseEntity<AnnouncementResponse> created =
        restTemplate.exchange(
            url("/admin/system/announcements"),
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            AnnouncementResponse.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(testEventCollector.getAnnouncementEvents())
        .extracting(AnnouncementEvent::title)
        .contains("진행중");
  }

  @Test
  @DisplayName("공지 등록 - 종료일 없음이면 endsAt null 분기로 이벤트 발행")
  void 공지등록_종료없음_이벤트발행() {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = authedJson(admin);

    AnnouncementCreateRequest request =
        new AnnouncementCreateRequest("무기한", null, "INFO", clock.instant().minusSeconds(60), null);

    restTemplate.exchange(
        url("/admin/system/announcements"),
        HttpMethod.POST,
        new HttpEntity<>(request, headers),
        AnnouncementResponse.class);

    assertThat(testEventCollector.getAnnouncementEvents())
        .extracting(AnnouncementEvent::title)
        .contains("무기한");
  }

  @Test
  @DisplayName("공지 등록 - 미래 시작이면 이벤트 없음")
  void 공지등록_미래시작_이벤트없음() {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = authedJson(admin);
    Instant future = clock.instant().plus(Duration.ofHours(1));

    AnnouncementCreateRequest request =
        new AnnouncementCreateRequest("예약", "내용", "INFO", future, future.plus(Duration.ofHours(2)));

    restTemplate.exchange(
        url("/admin/system/announcements"),
        HttpMethod.POST,
        new HttpEntity<>(request, headers),
        AnnouncementResponse.class);

    assertThat(testEventCollector.getAnnouncementEvents()).isEmpty();
  }

  @Test
  @DisplayName("공지 등록 - 종료일이 이미 지남이면 이벤트 없음")
  void 공지등록_종료지남_이벤트없음() {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = authedJson(admin);
    Instant past = clock.instant().minus(Duration.ofDays(2));

    AnnouncementCreateRequest request =
        new AnnouncementCreateRequest(
            "지난 공지", "내용", "WARNING", past, clock.instant().minus(Duration.ofDays(1)));

    restTemplate.exchange(
        url("/admin/system/announcements"),
        HttpMethod.POST,
        new HttpEntity<>(request, headers),
        AnnouncementResponse.class);

    assertThat(testEventCollector.getAnnouncementEvents()).isEmpty();
  }

  @Test
  @DisplayName("공지 등록 - endsAt이 startsAt보다 이전이면 400 VALIDATION_FAILED")
  void 공지등록_역순날짜_400() {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = authedJson(admin);
    Instant now = clock.instant();

    AnnouncementCreateRequest request =
        new AnnouncementCreateRequest("역순", null, "INFO", now, now.minusSeconds(60));

    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/admin/system/announcements"),
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
  }

  @Test
  @DisplayName("공지 수정 - title/content/type/active/startsAt/endsAt 개별 patch")
  void 공지수정_필드별_patch() {
    AnnouncementResponse created = createAnnouncement("원본", "INFO");

    patch(created.id(), "{\"title\":\"새 제목\"}");
    patch(created.id(), "{\"content\":\"새 내용\"}");
    patch(created.id(), "{\"type\":\"WARNING\"}");
    patch(created.id(), "{\"active\":false}");
    Instant newStart = clock.instant().minusSeconds(30);
    Instant newEnd = newStart.plus(Duration.ofHours(3));
    patch(created.id(), "{\"startsAt\":\"" + newStart + "\"}");
    patch(created.id(), "{\"endsAt\":\"" + newEnd + "\"}");

    List<AnnouncementResponse> list = fetchAnnouncements();
    AnnouncementResponse updated =
        list.stream().filter(a -> a.id().equals(created.id())).findFirst().orElseThrow();
    assertThat(updated.title()).isEqualTo("새 제목");
    assertThat(updated.content()).isEqualTo("새 내용");
    assertThat(updated.type()).isEqualTo("WARNING");
    assertThat(updated.active()).isFalse();
  }

  @Test
  @DisplayName("공지 수정 - active=true + 현재 기간이면 이벤트 발행")
  void 공지수정_활성기간_이벤트발행() {
    AnnouncementResponse created = createAnnouncement("원본", "INFO");
    testEventCollector.reset();

    patch(created.id(), "{\"content\":\"수정된 내용\"}");

    assertThat(testEventCollector.getAnnouncementEvents())
        .extracting(AnnouncementEvent::id)
        .contains(created.id());
  }

  @Test
  @DisplayName("공지 수정 - active=false면 이벤트 없음")
  void 공지수정_비활성_이벤트없음() {
    AnnouncementResponse created = createAnnouncement("원본", "INFO");
    testEventCollector.reset();

    patch(created.id(), "{\"active\":false}");

    assertThat(testEventCollector.getAnnouncementEvents()).isEmpty();
  }

  @Test
  @DisplayName("공지 수정 - 미래 시작으로 변경 시 이벤트 없음")
  void 공지수정_미래시작_이벤트없음() {
    AnnouncementResponse created = createAnnouncement("원본", "INFO");
    testEventCollector.reset();

    Instant future = clock.instant().plus(Duration.ofHours(1));
    Instant futureEnd = future.plus(Duration.ofHours(1));
    patch(created.id(), "{\"startsAt\":\"" + future + "\",\"endsAt\":\"" + futureEnd + "\"}");

    assertThat(testEventCollector.getAnnouncementEvents()).isEmpty();
  }

  @Test
  @DisplayName("공지 수정 - 역순 날짜 400")
  void 공지수정_역순날짜_400() {
    AnnouncementResponse created = createAnnouncement("원본", "INFO");
    Instant past = clock.instant().minusSeconds(3600);

    ResponseEntity<ErrorBody> response =
        patchExpectError(created.id(), "{\"endsAt\":\"" + past + "\"}");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
  }

  @Test
  @DisplayName("공지 삭제 - AnnouncementEvent 발행")
  void 공지삭제_이벤트발행() {
    AnnouncementResponse created = createAnnouncement("삭제대상", "INFO");
    testEventCollector.reset();
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    ResponseEntity<Void> response =
        restTemplate.exchange(
            url("/admin/system/announcements/" + created.id()),
            HttpMethod.DELETE,
            new HttpEntity<>(headers),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(testEventCollector.getAnnouncementEvents())
        .extracting(AnnouncementEvent::id)
        .contains(created.id());
  }

  @Test
  @DisplayName("공지 목록 조회 - admin fetch join 경로 실행")
  void 공지목록_조회() {
    createAnnouncement("목록 첫번째", "INFO");
    createAnnouncement("목록 두번째", "WARNING");
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    List<AnnouncementResponse> list = fetchAnnouncements();
    assertThat(list).hasSizeGreaterThanOrEqualTo(2);
  }

  @Test
  @DisplayName("감사 로그 조회 - action 필터 일치 항목만 반환")
  void 감사로그_action_필터() {
    Member admin = testAuthHelper.createAdmin();
    seedAuditLog(admin, "ACTION_A", clock.instant());
    seedAuditLog(admin, "ACTION_B", clock.instant());
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    ResponseEntity<AuditLogListResponse> response =
        restTemplate.exchange(
            url("/admin/system/audit-logs?action=ACTION_A"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            AuditLogListResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().content())
        .allSatisfy(entry -> assertThat(entry.action()).isEqualTo("ACTION_A"));
    assertThat(response.getBody().totalElements()).isEqualTo(1L);
  }

  @Test
  @DisplayName("감사 로그 조회 - dateFrom/dateTo 필터")
  void 감사로그_날짜_필터() {
    Member admin = testAuthHelper.createAdmin();
    seedAuditLog(admin, "ANY", clock.instant());
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);
    Instant from = Instant.now().minus(Duration.ofMinutes(5));
    Instant to = Instant.now().plus(Duration.ofMinutes(5));

    ResponseEntity<AuditLogListResponse> response =
        restTemplate.exchange(
            url("/admin/system/audit-logs?dateFrom=" + from + "&dateTo=" + to + "&action=ANY"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            AuditLogListResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().totalElements()).isGreaterThanOrEqualTo(1L);
  }

  private AnnouncementResponse createAnnouncement(String title, String type) {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = authedJson(admin);
    Instant now = clock.instant();
    AnnouncementCreateRequest request =
        new AnnouncementCreateRequest(
            title, "body", type, now.minusSeconds(60), now.plus(Duration.ofHours(1)));
    return restTemplate
        .exchange(
            url("/admin/system/announcements"),
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            AnnouncementResponse.class)
        .getBody();
  }

  private void patch(Long id, String body) {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = authedJson(admin);
    ResponseEntity<AnnouncementResponse> response =
        restTemplate.exchange(
            url("/admin/system/announcements/" + id),
            HttpMethod.PATCH,
            new HttpEntity<>(body, headers),
            AnnouncementResponse.class);
    assertThat(response.getStatusCode().is2xxSuccessful()).as("PATCH body=%s", body).isTrue();
  }

  private ResponseEntity<ErrorBody> patchExpectError(Long id, String body) {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = authedJson(admin);
    return restTemplate.exchange(
        url("/admin/system/announcements/" + id),
        HttpMethod.PATCH,
        new HttpEntity<>(body, headers),
        ErrorBody.class);
  }

  private List<AnnouncementResponse> fetchAnnouncements() {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);
    return restTemplate
        .exchange(
            url("/admin/system/announcements"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            new ParameterizedTypeReference<List<AnnouncementResponse>>() {})
        .getBody();
  }

  private void seedAuditLog(Member admin, String action, Instant ignored) {
    transactionTemplate.executeWithoutResult(
        status ->
            auditLogRepository.save(new AuditLog(admin, action, "TEST", null, null, "127.0.0.1")));
  }

  private HttpHeaders authedJson(Member admin) {
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }
}
