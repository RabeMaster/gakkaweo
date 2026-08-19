package com.gakkaweo.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("Security 접근 매트릭스 통합 테스트")
class SecurityConfigTest extends IntegrationTestBase {

  @Test
  @DisplayName("public 엔드포인트 - 미인증 접근 허용")
  void public_미인증_허용() {
    ResponseEntity<String> health = restTemplate.getForEntity(url("/health"), String.class);
    assertThat(health.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("/auth/me - 미인증 401")
  void me_미인증_401() {
    ResponseEntity<String> response = restTemplate.getForEntity(url("/auth/me"), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("/daily/history - 미인증 401")
  void history_미인증_401() {
    ResponseEntity<String> response =
        restTemplate.getForEntity(
            url("/daily/history?sentenceId=550e8400-e29b-41d4-a716-446655440000"), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("/admin/** - 미인증 401")
  void admin_미인증_401() {
    ResponseEntity<String> response =
        restTemplate.getForEntity(url("/admin/users?page=0&size=10"), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("/admin/** - USER 403")
  void admin_USER_권한_403() {
    Member user = testAuthHelper.createMember();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(user);

    ResponseEntity<String> response =
        restTemplate.exchange(
            url("/admin/users?page=0&size=10"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("/admin/** - ADMIN 200")
  void admin_ADMIN_권한_200() {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    ResponseEntity<String> response =
        restTemplate.exchange(
            url("/admin/users?page=0&size=10"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("/daily/today - 미인증 200 (public)")
  void daily_today_미인증_200() {
    testAuthHelper.createTodaySentence("안녕하세요");
    ResponseEntity<String> response = restTemplate.getForEntity(url("/daily/today"), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("/ranking/today - 미인증 200")
  void ranking_today_미인증_200() {
    ResponseEntity<String> response =
        restTemplate.getForEntity(url("/ranking/today"), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
