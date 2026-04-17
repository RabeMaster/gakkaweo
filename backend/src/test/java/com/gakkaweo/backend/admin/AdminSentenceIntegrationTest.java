package com.gakkaweo.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.admin.dto.DuplicateCheckRequest;
import com.gakkaweo.backend.admin.dto.DuplicateCheckResponse;
import com.gakkaweo.backend.admin.dto.ScheduleRequest;
import com.gakkaweo.backend.admin.dto.SentenceCreateRequest;
import com.gakkaweo.backend.admin.dto.SentenceListResponse;
import com.gakkaweo.backend.admin.dto.SentenceResponse;
import com.gakkaweo.backend.admin.dto.SentenceStatsResponse;
import com.gakkaweo.backend.admin.dto.SentenceUpdateRequest;
import com.gakkaweo.backend.admin.dto.SimilarityTestRequest;
import com.gakkaweo.backend.admin.dto.SimilarityTestResponse;
import com.gakkaweo.backend.admin.dto.UnusedCountResponse;
import com.gakkaweo.backend.common.exception.ErrorBody;
import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@DisplayName("Admin 문장 관리 통합 테스트")
class AdminSentenceIntegrationTest extends IntegrationTestBase {

  @Autowired DailySentenceRepository dailySentenceRepository;

  @Test
  @DisplayName("등록 - 201 + SENTENCE_DUPLICATE(409)")
  void 등록_중복() {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = authedJson(admin);

    ResponseEntity<SentenceResponse> created =
        restTemplate.exchange(
            url("/admin/sentences"),
            HttpMethod.POST,
            new HttpEntity<>(new SentenceCreateRequest("새 문장 하나"), headers),
            SentenceResponse.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    ResponseEntity<ErrorBody> dup =
        restTemplate.exchange(
            url("/admin/sentences"),
            HttpMethod.POST,
            new HttpEntity<>(new SentenceCreateRequest("새 문장 하나"), headers),
            ErrorBody.class);
    assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(dup.getBody().code()).isEqualTo("SENTENCE_DUPLICATE");
  }

  @Test
  @DisplayName("수정 - 출제된 문장은 SENTENCE_ALREADY_USED(400)")
  void 수정_출제후_불가() {
    Member admin = testAuthHelper.createAdmin();
    DailySentence sentence = testAuthHelper.createTodaySentence("오늘 문장");

    HttpHeaders headers = authedJson(admin);

    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/admin/sentences/" + sentence.getPublicId()),
            HttpMethod.PATCH,
            new HttpEntity<>(new SentenceUpdateRequest("수정된 문장"), headers),
            ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("SENTENCE_ALREADY_USED");
  }

  @Test
  @DisplayName("수정 - 미출제 문장은 성공")
  void 수정_성공() {
    Member admin = testAuthHelper.createAdmin();
    DailySentence sentence = testAuthHelper.createActiveSentence("미출제 문장");

    HttpHeaders headers = authedJson(admin);
    ResponseEntity<SentenceResponse> response =
        restTemplate.exchange(
            url("/admin/sentences/" + sentence.getPublicId()),
            HttpMethod.PATCH,
            new HttpEntity<>(new SentenceUpdateRequest("바뀐 문장"), headers),
            SentenceResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().sentence()).isEqualTo("바뀐 문장");
  }

  @Test
  @DisplayName("삭제 - 미출제만 가능")
  void 삭제_미출제() {
    Member admin = testAuthHelper.createAdmin();
    DailySentence sentence = testAuthHelper.createActiveSentence("삭제 대상");

    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);
    ResponseEntity<Void> response =
        restTemplate.exchange(
            url("/admin/sentences/" + sentence.getPublicId()),
            HttpMethod.DELETE,
            new HttpEntity<>(headers),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(dailySentenceRepository.findByPublicId(sentence.getPublicId())).isEmpty();
  }

  @Test
  @DisplayName("삭제 - 출제된 문장은 SENTENCE_ALREADY_USED")
  void 삭제_출제됨_400() {
    Member admin = testAuthHelper.createAdmin();
    DailySentence sentence = testAuthHelper.createTodaySentence("출제된 문장");

    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);
    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/admin/sentences/" + sentence.getPublicId()),
            HttpMethod.DELETE,
            new HttpEntity<>(headers),
            ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("SENTENCE_ALREADY_USED");
  }

  @Test
  @DisplayName("스케줄 지정 - 성공 + SENTENCE_ALREADY_SCHEDULED(409)")
  void 스케줄_중복() {
    Member admin = testAuthHelper.createAdmin();
    DailySentence a = testAuthHelper.createActiveSentence("A 문장");
    DailySentence b = testAuthHelper.createActiveSentence("B 문장");

    LocalDate tomorrow = LocalDate.now(clock).plusDays(1);

    HttpHeaders headers = authedJson(admin);

    ResponseEntity<SentenceResponse> first =
        restTemplate.exchange(
            url("/admin/sentences/" + a.getPublicId() + "/schedule"),
            HttpMethod.POST,
            new HttpEntity<>(new ScheduleRequest(tomorrow), headers),
            SentenceResponse.class);
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<ErrorBody> conflict =
        restTemplate.exchange(
            url("/admin/sentences/" + b.getPublicId() + "/schedule"),
            HttpMethod.POST,
            new HttpEntity<>(new ScheduleRequest(tomorrow), headers),
            ErrorBody.class);

    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(conflict.getBody().code()).isEqualTo("SENTENCE_ALREADY_SCHEDULED");
  }

  @Test
  @DisplayName("스케줄 지정 - 과거 날짜는 VALIDATION_FAILED(400)")
  void 과거날짜_400() {
    Member admin = testAuthHelper.createAdmin();
    DailySentence sentence = testAuthHelper.createActiveSentence("스케줄용");

    LocalDate yesterday = LocalDate.now(clock).minusDays(1);

    HttpHeaders headers = authedJson(admin);
    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/admin/sentences/" + sentence.getPublicId() + "/schedule"),
            HttpMethod.POST,
            new HttpEntity<>(new ScheduleRequest(yesterday), headers),
            ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
  }

  @Test
  @DisplayName("목록 조회 - 상태 필터 ACTIVE")
  void 목록_ACTIVE_필터() {
    Member admin = testAuthHelper.createAdmin();
    testAuthHelper.createActiveSentence("활성 A");
    testAuthHelper.createActiveSentence("활성 B");
    testAuthHelper.createTodaySentence("오늘 문장");

    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);
    ResponseEntity<SentenceListResponse> response =
        restTemplate.exchange(
            url("/admin/sentences?status=ACTIVE&page=0&size=20"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            SentenceListResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().sentences()).hasSize(2);
  }

  @Test
  @DisplayName("상세 조회 - 존재 시 200")
  void 상세_200() {
    Member admin = testAuthHelper.createAdmin();
    DailySentence sentence = testAuthHelper.createActiveSentence("상세 대상");

    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);
    ResponseEntity<SentenceResponse> response =
        restTemplate.exchange(
            url("/admin/sentences/" + sentence.getPublicId()),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            SentenceResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().sentence()).isEqualTo("상세 대상");
  }

  @Test
  @DisplayName("상세 통계 - 0 참여")
  void 상세_통계() {
    Member admin = testAuthHelper.createAdmin();
    DailySentence sentence = testAuthHelper.createActiveSentence("통계 대상");

    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);
    ResponseEntity<SentenceStatsResponse> response =
        restTemplate.exchange(
            url("/admin/sentences/" + sentence.getPublicId() + "/stats"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            SentenceStatsResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().totalSessions()).isEqualTo(0);
  }

  @Test
  @DisplayName("미사용 문장 수 조회")
  void 미사용_수() {
    Member admin = testAuthHelper.createAdmin();
    testAuthHelper.createActiveSentence("A");
    testAuthHelper.createActiveSentence("B");

    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);
    ResponseEntity<UnusedCountResponse> response =
        restTemplate.exchange(
            url("/admin/sentences/unused-count"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            UnusedCountResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().count()).isEqualTo(2);
  }

  @Test
  @DisplayName("similarity 테스트 - TestSimilarityClient 기반 점수 반환")
  void 유사도_테스트() {
    Member admin = testAuthHelper.createAdmin();
    testSimilarityClient.program("바람", new BigDecimal("77.7"));

    HttpHeaders headers = authedJson(admin);
    ResponseEntity<SimilarityTestResponse> response =
        restTemplate.exchange(
            url("/admin/sentences/similarity-test"),
            HttpMethod.POST,
            new HttpEntity<>(new SimilarityTestRequest("하늘", "바람"), headers),
            SimilarityTestResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().similarity()).isEqualByComparingTo("77.7");
  }

  @Test
  @DisplayName("중복 검사 - 80% 이상 유사한 문장 반환")
  void 중복_검사() {
    Member admin = testAuthHelper.createAdmin();
    testAuthHelper.createActiveSentence("비슷한 문장 있음");
    testSimilarityClient.program("새 문장 검사", new BigDecimal("85.0"));

    HttpHeaders headers = authedJson(admin);
    ResponseEntity<DuplicateCheckResponse> response =
        restTemplate.exchange(
            url("/admin/sentences/duplicate-check"),
            HttpMethod.POST,
            new HttpEntity<>(new DuplicateCheckRequest("새 문장 검사"), headers),
            DuplicateCheckResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().hasDuplicate()).isTrue();
  }

  @Test
  @DisplayName("unschedule - 스케줄 해제")
  void 스케줄_해제() {
    Member admin = testAuthHelper.createAdmin();
    DailySentence sentence = testAuthHelper.createActiveSentence("해제 대상");
    sentence.setScheduledAt(LocalDate.now(clock).plusDays(1));
    dailySentenceRepository.save(sentence);

    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);
    ResponseEntity<SentenceResponse> response =
        restTemplate.exchange(
            url("/admin/sentences/" + sentence.getPublicId() + "/schedule"),
            HttpMethod.DELETE,
            new HttpEntity<>(headers),
            SentenceResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().scheduledAt()).isNull();
  }

  private HttpHeaders authedJson(Member admin) {
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }
}
