package com.gakkaweo.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.admin.dto.RoleChangeRequest;
import com.gakkaweo.backend.admin.service.AdminUserService;
import com.gakkaweo.backend.auth.jwt.JwtProvider;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("Security 접근 매트릭스 통합 테스트")
class SecurityConfigTest extends IntegrationTestBase {

  @Autowired MemberRepository memberRepository;
  @Autowired AdminUserService adminUserService;
  @Autowired JwtProvider jwtProvider;

  @Test
  @DisplayName("관리자 강등 후 이미 발급한 access token으로 관리자 API 접근 불가")
  void 기존_토큰_관리자_강등_반영() {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);
    Member superAdmin = testAuthHelper.createSuperAdmin();

    adminUserService.changeRole(
        admin.getPublicId(), superAdmin.getPublicId(), new RoleChangeRequest("USER"), "127.0.0.1");

    ResponseEntity<String> response =
        restTemplate.exchange(
            url("/admin/users?page=0&size=10"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("차단 후 이미 발급한 access token으로 공개 API를 호출해도 차단 오류")
  void 기존_토큰_계정_차단_반영() {
    Member member = testAuthHelper.createMember();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(member);

    adminUserService.banUser(
        member.getPublicId(), testAuthHelper.createSuperAdmin().getPublicId(), "127.0.0.1");

    ResponseEntity<String> response =
        restTemplate.exchange(
            url("/ranking/today"), HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody()).contains("MEMBER_BANNED");
  }

  @Test
  @DisplayName("차단 계정도 유효한 기존 access token으로 로그아웃하여 쿠키를 정리할 수 있음")
  void 차단_계정_로그아웃_허용() {
    Member member = testAuthHelper.createMember();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(member);

    adminUserService.banUser(
        member.getPublicId(), testAuthHelper.createSuperAdmin().getPublicId(), "127.0.0.1");

    var response =
        restTemplate.exchange(
            url("/auth/logout"), HttpMethod.POST, new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).hasSize(4);
  }

  @Test
  @DisplayName("회원 삭제 후 이미 발급한 access token으로 인증 API 접근 불가")
  void 기존_토큰_회원_삭제_반영() {
    Member member = testAuthHelper.createMember();

    // refresh token FK 없이 access token만 발급하여 회원 삭제 상황을 재현합니다.
    HttpHeaders headers = new HttpHeaders();
    headers.set(
        HttpHeaders.COOKIE,
        "access_token="
            + jwtProvider.createAccessToken(member.getPublicId(), member.getRole().name()));

    memberRepository.delete(member);

    ResponseEntity<String> response =
        restTemplate.exchange(
            url("/auth/me"), HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

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
