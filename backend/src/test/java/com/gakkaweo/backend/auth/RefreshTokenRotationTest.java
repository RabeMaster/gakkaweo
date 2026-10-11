package com.gakkaweo.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.admin.service.AdminUserService;
import com.gakkaweo.backend.auth.dto.TokenPair;
import com.gakkaweo.backend.auth.service.AuthSessionService;
import com.gakkaweo.backend.common.exception.ErrorBody;
import com.gakkaweo.backend.common.util.HashUtils;
import com.gakkaweo.backend.domain.auth.entity.RefreshToken;
import com.gakkaweo.backend.domain.auth.repository.RefreshTokenRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.support.IntegrationTestBase;
import com.gakkaweo.backend.support.TestClock;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("Refresh Token Rotation 통합 테스트")
class RefreshTokenRotationTest extends IntegrationTestBase {

  @Autowired AuthSessionService authSessionService;
  @Autowired RefreshTokenRepository refreshTokenRepository;
  @Autowired Clock clock;
  @Autowired AdminUserService adminUserService;

  @Test
  @DisplayName("정상 회전 - RT1 revoked, RT2 발급")
  void 정상_회전() {
    Member member = testAuthHelper.createMember();
    TokenPair initial = testAuthHelper.issueTokens(member);

    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.COOKIE, "refresh_token=" + initial.refreshToken());

    ResponseEntity<Void> response =
        restTemplate.exchange(
            url("/auth/refresh"), HttpMethod.POST, new HttpEntity<>(headers), Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    RefreshToken old =
        refreshTokenRepository
            .findByTokenHash(HashUtils.sha256Hex(initial.refreshToken()))
            .orElseThrow();
    assertThat(old.isRevoked()).isTrue();

    List<RefreshToken> all = refreshTokenRepository.findByMemberId(member.getId());

    assertThat(all).hasSize(2);
  }

  @Test
  @DisplayName("같은 갱신 토큰의 동시 갱신은 한 번만 성공하며 재사용 후 로그인 세션을 폐기한다")
  void 동시_회전_한번만_허용() throws Exception {
    Member member = testAuthHelper.createMember();
    TokenPair initial = testAuthHelper.issueTokens(member);

    CyclicBarrier barrier = new CyclicBarrier(2);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return restTemplate.exchange(
                    url("/auth/refresh"),
                    HttpMethod.POST,
                    new HttpEntity<>(testAuthHelper.refreshCookieHeaderFor(initial.refreshToken())),
                    ErrorBody.class);
              });

      var second =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return restTemplate.exchange(
                    url("/auth/refresh"),
                    HttpMethod.POST,
                    new HttpEntity<>(testAuthHelper.refreshCookieHeaderFor(initial.refreshToken())),
                    ErrorBody.class);
              });

      var responses = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

      assertThat(responses)
          .extracting(ResponseEntity::getStatusCode)
          .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.UNAUTHORIZED);
    }

    List<RefreshToken> tokens = refreshTokenRepository.findByMemberId(member.getId());

    assertThat(tokens).hasSize(2).allMatch(RefreshToken::isRevoked);
  }

  @Test
  @DisplayName("만료 시각과 같으면 거부하며 폐기 상태는 예외 후에도 DB에 남음")
  void 만료_경계_폐기_유지() {
    Member member = testAuthHelper.createMember();
    TokenPair initial = testAuthHelper.issueTokens(member);
    RefreshToken existing =
        refreshTokenRepository
            .findByTokenHash(HashUtils.sha256Hex(initial.refreshToken()))
            .orElseThrow();
    ((TestClock) clock).setInstant(existing.getExpiresAt());

    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/auth/refresh"),
            HttpMethod.POST,
            new HttpEntity<>(testAuthHelper.refreshCookieHeaderFor(initial.refreshToken())),
            ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().code()).isEqualTo("EXPIRED_TOKEN");
    assertThat(
            refreshTokenRepository
                .findByTokenHash(HashUtils.sha256Hex(initial.refreshToken()))
                .orElseThrow()
                .isRevoked())
        .isTrue();
    assertThat(refreshTokenRepository.findByMemberId(member.getId())).hasSize(1);
  }

  @Test
  @DisplayName("재사용 감지 - 401과 해당 세션의 모든 갱신 토큰 폐기")
  void 재사용_감지() {
    Member member = testAuthHelper.createMember();
    TokenPair initial = testAuthHelper.issueTokens(member);

    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.COOKIE, "refresh_token=" + initial.refreshToken());

    restTemplate.exchange(
        url("/auth/refresh"), HttpMethod.POST, new HttpEntity<>(headers), Void.class);

    ResponseEntity<ErrorBody> reuse =
        restTemplate.exchange(
            url("/auth/refresh"), HttpMethod.POST, new HttpEntity<>(headers), ErrorBody.class);

    assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(reuse.getBody().code()).isEqualTo("REFRESH_TOKEN_REUSE_DETECTED");

    List<RefreshToken> all = refreshTokenRepository.findByMemberId(member.getId());

    assertThat(all).allMatch(RefreshToken::isRevoked);
  }

  @Test
  @DisplayName("만료 - Clock 7일 경과 → 401 EXPIRED_TOKEN")
  void 만료_401() {
    Member member = testAuthHelper.createMember();
    TokenPair initial = testAuthHelper.issueTokens(member);

    TestClock testClock = (TestClock) clock;
    testClock.advanceBy(Duration.ofDays(8));

    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.COOKIE, "refresh_token=" + initial.refreshToken());

    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/auth/refresh"), HttpMethod.POST, new HttpEntity<>(headers), ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().code()).isEqualTo("EXPIRED_TOKEN");
  }

  @Test
  @DisplayName("차단 계정 - refresh 시 403 MEMBER_BANNED")
  void 차단_계정_refresh() {
    Member member = testAuthHelper.createMember();
    TokenPair initial = testAuthHelper.issueTokens(member);

    adminUserService.banUser(
        member.getPublicId(), testAuthHelper.createSuperAdmin().getPublicId(), "127.0.0.1");

    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.COOKIE, "refresh_token=" + initial.refreshToken());

    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/auth/refresh"), HttpMethod.POST, new HttpEntity<>(headers), ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().code()).isEqualTo("MEMBER_BANNED");
  }
}
