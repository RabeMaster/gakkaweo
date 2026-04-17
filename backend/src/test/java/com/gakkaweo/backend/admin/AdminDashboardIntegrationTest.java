package com.gakkaweo.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.admin.dto.DateStatsResponse;
import com.gakkaweo.backend.admin.dto.FullRankingResponse;
import com.gakkaweo.backend.admin.dto.GuessLogResponse;
import com.gakkaweo.backend.admin.dto.TodayWidgetResponse;
import com.gakkaweo.backend.admin.dto.TrendDataResponse;
import com.gakkaweo.backend.common.exception.ErrorBody;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("Admin 대시보드 통합 테스트")
class AdminDashboardIntegrationTest extends IntegrationTestBase {

  @Autowired Clock dashClock;

  @Test
  @DisplayName("오늘 위젯 - 문장 있을 때 통계 반환")
  void 오늘_위젯() {
    testAuthHelper.createTodaySentence("오늘 문장");
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    ResponseEntity<TodayWidgetResponse> response =
        restTemplate.exchange(
            url("/admin/dashboard/today"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            TodayWidgetResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().sentence()).isEqualTo("오늘 문장");
    assertThat(response.getBody().totalParticipants()).isEqualTo(0);
  }

  @Test
  @DisplayName("오늘 위젯 - 문장 없을 때 SENTENCE_NOT_FOUND")
  void 오늘_위젯_문장없음() {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/admin/dashboard/today"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().code()).isEqualTo("SENTENCE_NOT_FOUND");
  }

  @Test
  @DisplayName("전체 랭킹 - 오늘 날짜 (기본)")
  void 전체_랭킹() {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    ResponseEntity<FullRankingResponse> response =
        restTemplate.exchange(
            url("/admin/dashboard/ranking"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            FullRankingResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().rankings()).isNotNull();
  }

  @Test
  @DisplayName("날짜별 통계 - 문장 존재 시 200")
  void 날짜별_통계() {
    testAuthHelper.createTodaySentence("오늘 문장");
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    LocalDate today = LocalDate.now(dashClock);

    ResponseEntity<DateStatsResponse> response =
        restTemplate.exchange(
            url("/admin/dashboard/stats/" + today),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            DateStatsResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().sentence()).isEqualTo("오늘 문장");
    assertThat(response.getBody().date()).isEqualTo(today);
  }

  @Test
  @DisplayName("추이 - 7일")
  void 추이_7일() {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    ResponseEntity<TrendDataResponse> response =
        restTemplate.exchange(
            url("/admin/dashboard/trends?days=7"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            TrendDataResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().trends()).hasSize(7);
  }

  @Test
  @DisplayName("추측 로그 - 문장 있을 때 빈 목록")
  void 추측로그() {
    testAuthHelper.createTodaySentence("오늘 문장");
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    LocalDate today = LocalDate.now(dashClock);

    ResponseEntity<GuessLogResponse> response =
        restTemplate.exchange(
            url("/admin/dashboard/guess-log?date=" + today),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            GuessLogResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().logs()).isEmpty();
  }
}
