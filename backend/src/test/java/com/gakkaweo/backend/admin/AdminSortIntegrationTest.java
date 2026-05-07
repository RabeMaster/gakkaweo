package com.gakkaweo.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.admin.dto.AdminUserResponse;
import com.gakkaweo.backend.admin.dto.AuditLogListResponse;
import com.gakkaweo.backend.admin.dto.AuditLogResponse;
import com.gakkaweo.backend.admin.dto.SentenceListResponse;
import com.gakkaweo.backend.admin.dto.SentenceResponse;
import com.gakkaweo.backend.admin.dto.UserListResponse;
import com.gakkaweo.backend.admin.service.AdminAuditService;
import com.gakkaweo.backend.common.exception.ErrorBody;
import com.gakkaweo.backend.domain.admin.entity.AuditAction;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("Admin 목록 동적 정렬 통합 테스트")
class AdminSortIntegrationTest extends IntegrationTestBase {

  @Autowired AdminAuditService adminAuditService;

  @Nested
  @DisplayName("/admin/users")
  class Users {

    @Test
    @DisplayName("사용자 목록 - 닉네임 오름차순 정렬 - 200")
    void 사용자_닉네임_오름차순() {
      Member admin = testAuthHelper.createAdmin();
      testAuthHelper.createMember();
      testAuthHelper.createMember();
      testAuthHelper.createMember();

      ResponseEntity<UserListResponse> response =
          restTemplate.exchange(
              url("/admin/users?sort=nickname,asc&page=0&size=20"),
              HttpMethod.GET,
              new HttpEntity<>(testAuthHelper.cookieHeaderFor(admin)),
              UserListResponse.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      List<String> nicknames =
          response.getBody().content().stream().map(AdminUserResponse::nickname).toList();
      assertThat(nicknames).isSortedAccordingTo(Comparator.naturalOrder());
    }

    @Test
    @DisplayName("사용자 목록 - 차단 필터 + 정렬 결합 - 200")
    void 사용자_필터_정렬_결합() {
      Member admin = testAuthHelper.createAdmin();
      testAuthHelper.createBannedMember();
      testAuthHelper.createBannedMember();
      testAuthHelper.createMember();

      ResponseEntity<UserListResponse> response =
          restTemplate.exchange(
              url("/admin/users?banned=true&sort=banned,desc&page=0&size=20"),
              HttpMethod.GET,
              new HttpEntity<>(testAuthHelper.cookieHeaderFor(admin)),
              UserListResponse.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().content())
          .isNotEmpty()
          .allSatisfy(user -> assertThat(user.banned()).isTrue());
    }

    @Test
    @DisplayName("사용자 목록 - sort 누락 - 200 + 기본 정렬")
    void 사용자_sort_누락_기본정렬() {
      Member admin = testAuthHelper.createAdmin();
      testAuthHelper.createMember();

      ResponseEntity<UserListResponse> response =
          restTemplate.exchange(
              url("/admin/users?page=0&size=20"),
              HttpMethod.GET,
              new HttpEntity<>(testAuthHelper.cookieHeaderFor(admin)),
              UserListResponse.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().content()).isNotEmpty();
    }

    @Test
    @DisplayName("사용자 목록 - 화이트리스트 외 필드 - 400 VALIDATION_FAILED")
    void 사용자_화이트리스트외_400() {
      Member admin = testAuthHelper.createAdmin();

      ResponseEntity<ErrorBody> response =
          restTemplate.exchange(
              url("/admin/users?sort=password,desc"),
              HttpMethod.GET,
              new HttpEntity<>(testAuthHelper.cookieHeaderFor(admin)),
              ErrorBody.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("사용자 목록 - 잘못된 방향 - 400 VALIDATION_FAILED")
    void 사용자_잘못된_방향_400() {
      Member admin = testAuthHelper.createAdmin();

      ResponseEntity<ErrorBody> response =
          restTemplate.exchange(
              url("/admin/users?sort=nickname,sideways"),
              HttpMethod.GET,
              new HttpEntity<>(testAuthHelper.cookieHeaderFor(admin)),
              ErrorBody.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
    }
  }

  @Nested
  @DisplayName("/admin/sentences")
  class Sentences {

    @Test
    @DisplayName("문장 목록 - sentence 오름차순 정렬 - 200")
    void 문장_오름차순() {
      Member admin = testAuthHelper.createAdmin();
      testAuthHelper.createActiveSentence("가장 먼저 오는 문장");
      testAuthHelper.createActiveSentence("나중에 오는 문장");
      testAuthHelper.createActiveSentence("마지막 문장");

      ResponseEntity<SentenceListResponse> response =
          restTemplate.exchange(
              url("/admin/sentences?sort=sentence,asc&page=0&size=20"),
              HttpMethod.GET,
              new HttpEntity<>(testAuthHelper.cookieHeaderFor(admin)),
              SentenceListResponse.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      List<String> sentences =
          response.getBody().content().stream().map(SentenceResponse::sentence).toList();
      assertThat(sentences).isSortedAccordingTo(Comparator.naturalOrder());
    }

    @Test
    @DisplayName("문장 목록 - usedAt 내림차순 정렬 - 출제일 최신순 + NULL은 마지막")
    void 문장_출제일_내림차순_NULL_마지막() {
      Member admin = testAuthHelper.createAdmin();
      testAuthHelper.createUsedSentence("이전 출제", LocalDate.of(2026, 1, 1));
      testAuthHelper.createUsedSentence("최근 출제", LocalDate.of(2026, 4, 1));
      testAuthHelper.createActiveSentence("미출제 1");
      testAuthHelper.createActiveSentence("미출제 2");

      ResponseEntity<SentenceListResponse> response =
          restTemplate.exchange(
              url("/admin/sentences?sort=usedAt,desc&page=0&size=20"),
              HttpMethod.GET,
              new HttpEntity<>(testAuthHelper.cookieHeaderFor(admin)),
              SentenceListResponse.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      List<SentenceResponse> sentences = response.getBody().content();
      assertThat(sentences).hasSize(4);
      assertThat(sentences.get(0).usedAt()).isEqualTo(LocalDate.of(2026, 4, 1));
      assertThat(sentences.get(1).usedAt()).isEqualTo(LocalDate.of(2026, 1, 1));
      assertThat(sentences.get(2).usedAt()).isNull();
      assertThat(sentences.get(3).usedAt()).isNull();
    }

    @Test
    @DisplayName("문장 목록 - scheduledAt 내림차순 정렬 - 예약일 최신순 + NULL은 마지막")
    void 문장_예약일_내림차순_NULL_마지막() {
      Member admin = testAuthHelper.createAdmin();
      testAuthHelper.createScheduledSentence("이른 예약", LocalDate.of(2026, 5, 1));
      testAuthHelper.createScheduledSentence("늦은 예약", LocalDate.of(2026, 6, 1));
      testAuthHelper.createActiveSentence("미예약 1");
      testAuthHelper.createActiveSentence("미예약 2");

      ResponseEntity<SentenceListResponse> response =
          restTemplate.exchange(
              url("/admin/sentences?sort=scheduledAt,desc&page=0&size=20"),
              HttpMethod.GET,
              new HttpEntity<>(testAuthHelper.cookieHeaderFor(admin)),
              SentenceListResponse.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      List<SentenceResponse> sentences = response.getBody().content();
      assertThat(sentences).hasSize(4);
      assertThat(sentences.get(0).scheduledAt()).isEqualTo(LocalDate.of(2026, 6, 1));
      assertThat(sentences.get(1).scheduledAt()).isEqualTo(LocalDate.of(2026, 5, 1));
      assertThat(sentences.get(2).scheduledAt()).isNull();
      assertThat(sentences.get(3).scheduledAt()).isNull();
    }

    @Test
    @DisplayName("문장 목록 - 화이트리스트 외 필드 - 400 VALIDATION_FAILED")
    void 문장_화이트리스트외_400() {
      Member admin = testAuthHelper.createAdmin();

      ResponseEntity<ErrorBody> response =
          restTemplate.exchange(
              url("/admin/sentences?sort=password,desc"),
              HttpMethod.GET,
              new HttpEntity<>(testAuthHelper.cookieHeaderFor(admin)),
              ErrorBody.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
    }
  }

  @Nested
  @DisplayName("/admin/system/audit-logs")
  class AuditLogs {

    @Test
    @DisplayName("감사 로그 - action 오름차순 정렬 - 200")
    void 감사로그_액션_오름차순() {
      Member admin = testAuthHelper.createAdmin();
      Member target = testAuthHelper.createMember();
      adminAuditService.log(
          admin, AuditAction.USER_BAN, target.getPublicId().toString(), null, "0.0.0.0");
      adminAuditService.log(admin, AuditAction.SENTENCE_CREATE, "fake-id", "테스트 문장", "0.0.0.0");
      adminAuditService.log(admin, AuditAction.RANKING_CACHE_RESET, null, null, "0.0.0.0");

      ResponseEntity<AuditLogListResponse> response =
          restTemplate.exchange(
              url("/admin/system/audit-logs?sort=action,asc&page=0&size=20"),
              HttpMethod.GET,
              new HttpEntity<>(testAuthHelper.cookieHeaderFor(admin)),
              AuditLogListResponse.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      List<String> actions =
          response.getBody().content().stream()
              .map(AuditLogResponse::action)
              .map(Enum::name)
              .toList();
      assertThat(actions).isSortedAccordingTo(Comparator.naturalOrder());
    }

    @Test
    @DisplayName("감사 로그 - 화이트리스트 외 필드 (admin.nickname) - 400 VALIDATION_FAILED")
    void 감사로그_화이트리스트외_400() {
      Member admin = testAuthHelper.createAdmin();

      ResponseEntity<ErrorBody> response =
          restTemplate.exchange(
              url("/admin/system/audit-logs?sort=admin.nickname,asc"),
              HttpMethod.GET,
              new HttpEntity<>(testAuthHelper.cookieHeaderFor(admin)),
              ErrorBody.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
    }
  }
}
