package com.gakkaweo.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.admin.dto.DuplicateCheckRequest;
import com.gakkaweo.backend.admin.dto.DuplicateCheckResponse;
import com.gakkaweo.backend.admin.dto.EmergencyReplaceRequest;
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
import com.gakkaweo.backend.domain.game.entity.DailySentenceStatus;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.ranking.event.DayChangeEvent;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("Admin 문장 관리 통합 테스트")
class AdminSentenceIntegrationTest extends IntegrationTestBase {

  @Autowired DailySentenceRepository dailySentenceRepository;
  @Autowired TransactionTemplate transactionTemplate;

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
  @DisplayName("목록 조회 - 잘못된 status 400")
  void 목록_status_잘못됨_400() {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/admin/sentences?status=INVALID&page=0&size=20"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
  }

  @Test
  @DisplayName("목록 조회 - 상태 필터 빈 문자열은 status=null과 동일하게 전체 반환")
  void 목록_공백status() {
    Member admin = testAuthHelper.createAdmin();
    testAuthHelper.createActiveSentence("빈 status A");
    testAuthHelper.createTodaySentence("오늘 문장");
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    ResponseEntity<SentenceListResponse> response =
        restTemplate.exchange(
            url("/admin/sentences?status=&page=0&size=20"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            SentenceListResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().sentences()).hasSize(2);
  }

  @Test
  @DisplayName("수정 - 동일 문장으로 업데이트는 중복 체크 건너뜀")
  void 수정_동일_문장() {
    Member admin = testAuthHelper.createAdmin();
    DailySentence sentence = testAuthHelper.createActiveSentence("그대로 유지");
    HttpHeaders headers = authedJson(admin);

    ResponseEntity<SentenceResponse> response =
        restTemplate.exchange(
            url("/admin/sentences/" + sentence.getPublicId()),
            HttpMethod.PATCH,
            new HttpEntity<>(new SentenceUpdateRequest("그대로 유지"), headers),
            SentenceResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().sentence()).isEqualTo("그대로 유지");
  }

  @Test
  @DisplayName("수정 - 다른 문장이지만 이미 존재하면 SENTENCE_DUPLICATE(409)")
  void 수정_중복_409() {
    Member admin = testAuthHelper.createAdmin();
    testAuthHelper.createActiveSentence("기존 문장");
    DailySentence target = testAuthHelper.createActiveSentence("변경 대상");
    HttpHeaders headers = authedJson(admin);

    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/admin/sentences/" + target.getPublicId()),
            HttpMethod.PATCH,
            new HttpEntity<>(new SentenceUpdateRequest("기존 문장"), headers),
            ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().code()).isEqualTo("SENTENCE_DUPLICATE");
  }

  @Test
  @DisplayName("중복 검사 - 유사도 낮으면 hasDuplicate=false")
  void 중복_검사_없음() {
    Member admin = testAuthHelper.createAdmin();
    testAuthHelper.createActiveSentence("전혀 다른 문장 하나");
    testSimilarityClient.setDefaultScore(new BigDecimal("30.0"));

    HttpHeaders headers = authedJson(admin);
    ResponseEntity<DuplicateCheckResponse> response =
        restTemplate.exchange(
            url("/admin/sentences/duplicate-check"),
            HttpMethod.POST,
            new HttpEntity<>(new DuplicateCheckRequest("새 문장"), headers),
            DuplicateCheckResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().hasDuplicate()).isFalse();
    assertThat(response.getBody().similarEntries()).isEmpty();
  }

  @Test
  @DisplayName("스케줄 - 이미 사용된 문장 SENTENCE_ALREADY_USED")
  void 스케줄_사용됨_400() {
    Member admin = testAuthHelper.createAdmin();
    DailySentence sentence = testAuthHelper.createTodaySentence("사용된 문장");
    HttpHeaders headers = authedJson(admin);

    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/admin/sentences/" + sentence.getPublicId() + "/schedule"),
            HttpMethod.POST,
            new HttpEntity<>(new ScheduleRequest(LocalDate.now(clock).plusDays(1)), headers),
            ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("SENTENCE_ALREADY_USED");
  }

  @Test
  @DisplayName("스케줄 - 동일 문장의 동일 날짜 재지정은 200")
  void 스케줄_재지정_동일날짜() {
    Member admin = testAuthHelper.createAdmin();
    DailySentence sentence = testAuthHelper.createActiveSentence("동일날짜 재지정");
    LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
    HttpHeaders headers = authedJson(admin);

    ResponseEntity<SentenceResponse> first =
        restTemplate.exchange(
            url("/admin/sentences/" + sentence.getPublicId() + "/schedule"),
            HttpMethod.POST,
            new HttpEntity<>(new ScheduleRequest(tomorrow), headers),
            SentenceResponse.class);
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<SentenceResponse> second =
        restTemplate.exchange(
            url("/admin/sentences/" + sentence.getPublicId() + "/schedule"),
            HttpMethod.POST,
            new HttpEntity<>(new ScheduleRequest(tomorrow), headers),
            SentenceResponse.class);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
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

  @Test
  @DisplayName("emergencyReplace - returnOldToPool=true (이전 문장 ACTIVE 복귀, DayChangeEvent 발행)")
  void 긴급교체_풀복귀() {
    Member admin = testAuthHelper.createAdmin();
    DailySentence old = testAuthHelper.createTodaySentence("교체 대상 (기존)");
    DailySentence replacement = testAuthHelper.createActiveSentence("교체 후보 (새 문장)");

    HttpHeaders headers = authedJson(admin);
    ResponseEntity<SentenceResponse> response =
        restTemplate.exchange(
            url("/admin/sentences/emergency-replace"),
            HttpMethod.POST,
            new HttpEntity<>(new EmergencyReplaceRequest(replacement.getPublicId(), true), headers),
            SentenceResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().publicId()).isEqualTo(replacement.getPublicId());

    DailySentence reloadedOld = dailySentenceRepository.findById(old.getId()).orElseThrow();
    assertThat(reloadedOld.getUsedAt()).isNull();
    assertThat(reloadedOld.getStatus()).isEqualTo(DailySentenceStatus.ACTIVE);

    DailySentence reloadedNew = dailySentenceRepository.findById(replacement.getId()).orElseThrow();
    assertThat(reloadedNew.getStatus()).isEqualTo(DailySentenceStatus.USED);

    assertThat(testEventCollector.getDayChangeEvents())
        .extracting(DayChangeEvent::newSentenceId)
        .contains(replacement.getPublicId());
  }

  @Test
  @DisplayName("emergencyReplace - returnOldToPool=false (이전 문장 DISABLED)")
  void 긴급교체_비활성처리() {
    Member admin = testAuthHelper.createAdmin();
    DailySentence old = testAuthHelper.createTodaySentence("교체 대상 2");
    DailySentence replacement = testAuthHelper.createActiveSentence("교체 후보 2");

    HttpHeaders headers = authedJson(admin);
    ResponseEntity<SentenceResponse> response =
        restTemplate.exchange(
            url("/admin/sentences/emergency-replace"),
            HttpMethod.POST,
            new HttpEntity<>(
                new EmergencyReplaceRequest(replacement.getPublicId(), false), headers),
            SentenceResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    DailySentence reloadedOld = dailySentenceRepository.findById(old.getId()).orElseThrow();
    assertThat(reloadedOld.getStatus()).isEqualTo(DailySentenceStatus.DISABLED);
  }

  @Test
  @DisplayName("emergencyReplace - 오늘 문장 없으면 SENTENCE_NOT_FOUND")
  void 긴급교체_오늘문장없음() {
    Member admin = testAuthHelper.createAdmin();
    DailySentence replacement = testAuthHelper.createActiveSentence("후보");

    HttpHeaders headers = authedJson(admin);
    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/admin/sentences/emergency-replace"),
            HttpMethod.POST,
            new HttpEntity<>(new EmergencyReplaceRequest(replacement.getPublicId(), true), headers),
            ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().code()).isEqualTo("SENTENCE_NOT_FOUND");
  }

  @Test
  @DisplayName("emergencyReplace - 존재하지 않는 newSentencePublicId SENTENCE_NOT_FOUND")
  void 긴급교체_없는문장() {
    Member admin = testAuthHelper.createAdmin();
    testAuthHelper.createTodaySentence("오늘");

    HttpHeaders headers = authedJson(admin);
    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/admin/sentences/emergency-replace"),
            HttpMethod.POST,
            new HttpEntity<>(new EmergencyReplaceRequest(UUID.randomUUID(), true), headers),
            ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().code()).isEqualTo("SENTENCE_NOT_FOUND");
  }

  @Test
  @DisplayName("emergencyReplace - newSentence가 이미 사용됨이면 SENTENCE_ALREADY_USED")
  void 긴급교체_이미사용() {
    Member admin = testAuthHelper.createAdmin();
    testAuthHelper.createTodaySentence("오늘");
    DailySentence alreadyUsed =
        transactionTemplate.execute(
            status -> {
              DailySentence s = new DailySentence("이미 사용된 과거 문장");
              s.setUsedAt(java.time.LocalDate.now(clock).minusDays(7));
              s.setStatus(DailySentenceStatus.USED);
              return dailySentenceRepository.save(s);
            });

    HttpHeaders headers = authedJson(admin);
    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/admin/sentences/emergency-replace"),
            HttpMethod.POST,
            new HttpEntity<>(new EmergencyReplaceRequest(alreadyUsed.getPublicId(), true), headers),
            ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("SENTENCE_ALREADY_USED");
  }

  @Test
  @DisplayName("emergencyReplace - newSentence가 DISABLED면 SENTENCE_NOT_FOUND")
  void 긴급교체_비활성문장() {
    Member admin = testAuthHelper.createAdmin();
    testAuthHelper.createTodaySentence("오늘");
    DailySentence disabled =
        transactionTemplate.execute(
            status -> {
              DailySentence s = new DailySentence("비활성 후보");
              s.setStatus(DailySentenceStatus.DISABLED);
              return dailySentenceRepository.save(s);
            });

    HttpHeaders headers = authedJson(admin);
    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/admin/sentences/emergency-replace"),
            HttpMethod.POST,
            new HttpEntity<>(new EmergencyReplaceRequest(disabled.getPublicId(), true), headers),
            ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().code()).isEqualTo("SENTENCE_NOT_FOUND");
  }

  private HttpHeaders authedJson(Member admin) {
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }
}
