package com.gakkaweo.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.auth.dto.TokenPair;
import com.gakkaweo.backend.auth.service.RefreshTokenService;
import com.gakkaweo.backend.common.exception.ErrorBody;
import com.gakkaweo.backend.domain.auth.entity.RefreshToken;
import com.gakkaweo.backend.domain.auth.repository.RefreshTokenRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.support.IntegrationTestBase;
import com.gakkaweo.backend.support.TestClock;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
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

  @Autowired RefreshTokenService refreshTokenService;
  @Autowired RefreshTokenRepository refreshTokenRepository;
  @Autowired Clock clock;

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

    RefreshToken old = refreshTokenService.findByRawToken(initial.refreshToken());
    assertThat(old.isRevoked()).isTrue();

    List<RefreshToken> all = refreshTokenRepository.findByMemberId(member.getId());
    assertThat(all).hasSize(2);
  }

  @Test
  @DisplayName("재사용 감지 - 401 + family 전체 revoke")
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

    // 차단 처리
    member.setBanned(true);
    member.setBannedAt(clock.instant());
    // 재조회/저장은 TX 밖이지만 영속성 컨텍스트에 남은 엔티티는 직접 flush 어려움 → 직접 DB 조작은 생략
    // 대신 신규 차단 멤버로 테스트하는 시나리오
    Member banned = testAuthHelper.createBannedMember();
    TokenPair bannedTokens = testAuthHelper.issueTokens(banned);

    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.COOKIE, "refresh_token=" + bannedTokens.refreshToken());

    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/auth/refresh"), HttpMethod.POST, new HttpEntity<>(headers), ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().code()).isEqualTo("MEMBER_BANNED");
  }
}
