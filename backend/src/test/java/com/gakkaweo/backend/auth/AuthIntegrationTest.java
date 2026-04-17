package com.gakkaweo.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.auth.dto.AuthResponse;
import com.gakkaweo.backend.auth.dto.LoginRequest;
import com.gakkaweo.backend.auth.dto.RegisterRequest;
import com.gakkaweo.backend.common.exception.ErrorBody;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("인증 플로우 통합 테스트")
class AuthIntegrationTest extends IntegrationTestBase {

  @Nested
  @DisplayName("회원가입")
  class Register {

    @Test
    @DisplayName("성공 - 201 + 쿠키 3종 발급")
    void 성공_201() {
      RegisterRequest request = new RegisterRequest("testuser", "password123");

      ResponseEntity<AuthResponse> response =
          restTemplate.postForEntity(url("/auth/register"), request, AuthResponse.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().role()).isEqualTo("USER");

      java.util.List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
      assertThat(cookies).isNotNull();
      assertThat(cookies.stream().anyMatch(c -> c.startsWith("access_token="))).isTrue();
      assertThat(cookies.stream().anyMatch(c -> c.startsWith("refresh_token="))).isTrue();
      assertThat(cookies.stream().anyMatch(c -> c.startsWith("has_session="))).isTrue();
    }

    @Test
    @DisplayName("중복 아이디 - 409 DUPLICATE_USERNAME")
    void 중복_409() {
      RegisterRequest request = new RegisterRequest("dupuser", "password123");
      restTemplate.postForEntity(url("/auth/register"), request, AuthResponse.class);

      ResponseEntity<ErrorBody> response =
          restTemplate.postForEntity(url("/auth/register"), request, ErrorBody.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(response.getBody().code()).isEqualTo("DUPLICATE_USERNAME");
    }

    @Test
    @DisplayName("유효성 실패 - 400 VALIDATION_FAILED")
    void 검증실패_400() {
      RegisterRequest request = new RegisterRequest("1badstart", "password123");

      ResponseEntity<ErrorBody> response =
          restTemplate.postForEntity(url("/auth/register"), request, ErrorBody.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
    }
  }

  @Nested
  @DisplayName("로그인")
  class Login {

    @Test
    @DisplayName("성공 - 200 + 쿠키")
    void 성공_200() {
      Member member = testAuthHelper.createMember();
      testAuthHelper.createLocalAccount(member, "loginuser", "password123");

      ResponseEntity<AuthResponse> response =
          restTemplate.postForEntity(
              url("/auth/login"), new LoginRequest("loginuser", "password123"), AuthResponse.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().publicId()).isEqualTo(member.getPublicId());
    }

    @Test
    @DisplayName("비밀번호 오류 - 401 INVALID_CREDENTIALS")
    void 비밀번호오류_401() {
      Member member = testAuthHelper.createMember();
      testAuthHelper.createLocalAccount(member, "wronguser", "password123");

      ResponseEntity<ErrorBody> response =
          restTemplate.postForEntity(
              url("/auth/login"), new LoginRequest("wronguser", "wrongpassword"), ErrorBody.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
      assertThat(response.getBody().code()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("존재하지 않는 계정 - 401 INVALID_CREDENTIALS")
    void 미존재_401() {
      ResponseEntity<ErrorBody> response =
          restTemplate.postForEntity(
              url("/auth/login"), new LoginRequest("ghost", "password123"), ErrorBody.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
      assertThat(response.getBody().code()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("차단 계정 - 403 MEMBER_BANNED")
    void 차단계정_403() {
      Member member = testAuthHelper.createBannedMember();
      testAuthHelper.createLocalAccount(member, "banneduser", "password123");

      ResponseEntity<ErrorBody> response =
          restTemplate.postForEntity(
              url("/auth/login"), new LoginRequest("banneduser", "password123"), ErrorBody.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(response.getBody().code()).isEqualTo("MEMBER_BANNED");
    }
  }

  @Nested
  @DisplayName("내 정보 조회 / 로그아웃")
  class MeLogout {

    @Test
    @DisplayName("쿠키 인증 me - 200")
    void me_200() {
      Member member = testAuthHelper.createMember();
      HttpHeaders headers = testAuthHelper.cookieHeaderFor(member);

      ResponseEntity<AuthResponse> response =
          restTemplate.exchange(
              url("/auth/me"), HttpMethod.GET, new HttpEntity<>(headers), AuthResponse.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().publicId()).isEqualTo(member.getPublicId());
    }

    @Test
    @DisplayName("미인증 me - 401")
    void me_401() {
      ResponseEntity<ErrorBody> response =
          restTemplate.getForEntity(url("/auth/me"), ErrorBody.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("로그아웃 - 200 + 쿠키 삭제(MaxAge=0)")
    void 로그아웃_200() {
      Member member = testAuthHelper.createMember();
      HttpHeaders headers = testAuthHelper.cookieHeaderFor(member);

      ResponseEntity<Void> response =
          restTemplate.exchange(
              url("/auth/logout"), HttpMethod.POST, new HttpEntity<>(headers), Void.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      java.util.List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
      assertThat(cookies).isNotNull();
      assertThat(cookies.stream().anyMatch(c -> c.contains("Max-Age=0"))).isTrue();
    }
  }
}
