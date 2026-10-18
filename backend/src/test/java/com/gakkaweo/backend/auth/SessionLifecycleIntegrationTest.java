package com.gakkaweo.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gakkaweo.backend.auth.dto.AuthResponse;
import com.gakkaweo.backend.auth.dto.LoginRequest;
import com.gakkaweo.backend.auth.dto.PasswordChangeRequest;
import com.gakkaweo.backend.auth.dto.TokenPair;
import com.gakkaweo.backend.auth.jwt.JwtProvider;
import com.gakkaweo.backend.auth.service.AuthSessionService;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.util.HashUtils;
import com.gakkaweo.backend.domain.auth.repository.AuthSessionRepository;
import com.gakkaweo.backend.domain.auth.repository.RefreshTokenRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.support.IntegrationTestBase;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("기기별 세션 폐기 및 비밀번호 변경 통합 테스트")
class SessionLifecycleIntegrationTest extends IntegrationTestBase {

  @Autowired StringRedisTemplate redisTemplate;
  @Autowired JwtProvider jwtProvider;
  @Autowired EntityManagerFactory entityManagerFactory;
  @Autowired MemberRepository memberRepository;
  @Autowired AuthSessionService authSessionService;
  @Autowired RefreshTokenRepository refreshTokenRepository;
  @Autowired AuthSessionRepository authSessionRepository;

  @Test
  @DisplayName("내부 세션 연결은 숫자 ID를 쓰고 JWT는 공개 UUID를 유지한다")
  void 세션_DB숫자ID_공개UUID_분리() {
    Member member = testAuthHelper.createMember();

    TokenPair pair = authSessionService.issue(member.getId());
    var claims = jwtProvider.parseAccessToken(pair.accessToken());
    var publicId = UUID.fromString(claims.get("sid", String.class));
    var session = authSessionRepository.findByPublicId(publicId).orElseThrow();

    assertThat(session.getId()).isPositive();
    assertThat(session.getPublicId()).isEqualTo(publicId);
    assertThat(claims.getSubject()).isEqualTo(member.getPublicId().toString());

    var reference =
        refreshTokenRepository
            .findReferenceByTokenHash(HashUtils.sha256Hex(pair.refreshToken()))
            .orElseThrow();

    assertThat(reference.getMemberId()).isEqualTo(member.getId());
    assertThat(reference.getSessionId()).isEqualTo(session.getId());

    TokenPair rotated = authSessionService.rotate(pair.refreshToken());

    assertThat(jwtProvider.parseAccessToken(rotated.accessToken()).get("sid", String.class))
        .isEqualTo(publicId.toString());
    assertThat(authSessionRepository.findById(session.getId()).orElseThrow().getPublicId())
        .isEqualTo(publicId);
    assertThat(me(rotated).getStatusCode()).isEqualTo(HttpStatus.OK);

    authSessionService.logout(null, rotated.refreshToken());

    assertThat(authSessionRepository.findById(session.getId()).orElseThrow().isRevoked()).isTrue();
    assertThat(me(rotated).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("세션 발급·폐기·삭제는 서비스 트랜잭션에서 DB에 저장된다")
  void 세션_발급_폐기_삭제_영속성() {
    Member member = testAuthHelper.createMember();

    TokenPair first = authSessionService.issue(member.getId());

    assertThat(authSessionRepository.findByMemberId(member.getId()).getFirst().getCreatedAt())
        .isNotNull();

    authSessionService.revokeAll(member.getId());

    assertThat(authSessionRepository.findByMemberId(member.getId())).allMatch(s -> s.isRevoked());
    assertThat(refreshTokenRepository.findByMemberId(member.getId())).allMatch(t -> t.isRevoked());
    assertThat(me(first).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    TokenPair fresh = authSessionService.issue(member.getId());

    assertThat(me(fresh).getStatusCode()).isEqualTo(HttpStatus.OK);

    authSessionService.deleteAll(member.getId());

    assertThat(authSessionRepository.findByMemberId(member.getId())).isEmpty();
    assertThat(refreshTokenRepository.findByMemberId(member.getId())).isEmpty();
    assertThat(memberRepository.findById(member.getId())).isPresent();
    assertThat(me(fresh).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("Redis를 초기화해도 DB 인증과 토큰 갱신은 유지된다")
  void Redis_초기화_DB인증_갱신_유지() {
    TokenPair pair = testAuthHelper.issueTokens(testAuthHelper.createMember());

    try (var connection = redisTemplate.getConnectionFactory().getConnection()) {
      connection.serverCommands().flushAll();
    }

    assertThat(me(pair).getStatusCode()).isEqualTo(HttpStatus.OK);

    TokenPair recovered = authSessionService.rotate(pair.refreshToken());

    assertThat(me(recovered).getStatusCode()).isEqualTo(HttpStatus.OK);

    authSessionService.logoutAll(
        UUID.fromString(jwtProvider.parseAccessToken(recovered.accessToken()).getSubject()));
    try (var connection = redisTemplate.getConnectionFactory().getConnection()) {
      connection.serverCommands().flushAll();
    }

    assertThatThrownBy(() -> authSessionService.rotate(recovered.refreshToken()))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("과거 Redis 활성 상태가 복원돼도 DB에서 폐기한 세션은 인증되지 않는다")
  void 과거_Redis상태_DB폐기_복구불가() {
    Member member = testAuthHelper.createMember();
    TokenPair pair = testAuthHelper.issueTokens(member);
    UUID sid =
        UUID.fromString(jwtProvider.parseAccessToken(pair.accessToken()).get("sid", String.class));

    assertThat(me(pair).getStatusCode()).isEqualTo(HttpStatus.OK);

    authSessionService.revokeAll(member.getId());

    // 이전 구현의 활성 상태가 복원되어도 DB의 폐기 결과를 우회할 수 없습니다.
    redisTemplate.opsForValue().set("auth:session:" + sid, member.getPublicId() + ":USER:ACTIVE");

    assertThat(me(pair).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThatThrownBy(() -> authSessionService.rotate(pair.refreshToken()))
        .isInstanceOf(BusinessException.class);
    assertThat(jwtProvider.parseAccessToken(pair.accessToken())).doesNotContainKey("sv");
  }

  private HttpHeaders cookies(TokenPair pair) {
    HttpHeaders headers = new HttpHeaders();
    headers.set(
        HttpHeaders.COOKIE,
        "access_token=" + pair.accessToken() + "; refresh_token=" + pair.refreshToken());

    return headers;
  }

  private ResponseEntity<String> me(TokenPair pair) {
    return restTemplate.exchange(
        url("/auth/me"), HttpMethod.GET, new HttpEntity<>(cookies(pair)), String.class);
  }

  private ResponseEntity<String> refresh(TokenPair pair) {
    return restTemplate.exchange(
        url("/auth/refresh"),
        HttpMethod.POST,
        new HttpEntity<>(testAuthHelper.refreshCookieHeaderFor(pair.refreshToken())),
        String.class);
  }

  @Test
  @DisplayName("현재 기기 로그아웃은 해당 세션의 액세스·갱신 토큰만 폐기한다")
  void 현재_기기만_로그아웃() {
    Member member = testAuthHelper.createMember();
    TokenPair first = testAuthHelper.issueTokens(member);
    TokenPair second = testAuthHelper.issueTokens(member);

    assertThat(
            restTemplate
                .exchange(
                    url("/auth/logout"),
                    HttpMethod.POST,
                    new HttpEntity<>(cookies(first)),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    assertThat(me(first).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(refresh(first).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(me(second).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(refresh(second).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("모든 기기 폐기는 Redis 초기화 후에도 유지된다")
  void 모든_기기_영속_폐기() {
    Member member = testAuthHelper.createMember();
    TokenPair first = testAuthHelper.issueTokens(member);
    TokenPair second = testAuthHelper.issueTokens(member);

    assertThat(
            restTemplate
                .exchange(
                    url("/auth/logout-all"),
                    HttpMethod.POST,
                    new HttpEntity<>(cookies(first)),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    try (var connection = redisTemplate.getConnectionFactory().getConnection()) {
      connection.serverCommands().flushAll();
    }

    assertThat(me(first).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(me(second).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(refresh(first).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(refresh(second).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("비밀번호 오입력은 400이며 정상 변경은 모든 기기를 폐기하고 새 비밀번호만 허용")
  void 비밀번호_검증_전체_폐기() {
    Member member = testAuthHelper.createMember();
    testAuthHelper.createLocalAccount(member, "pwchange", "password123");
    TokenPair first = testAuthHelper.issueTokens(member);
    TokenPair second = testAuthHelper.issueTokens(member);

    var capability =
        restTemplate.exchange(
            url("/auth/me"), HttpMethod.GET, new HttpEntity<>(cookies(first)), AuthResponse.class);

    assertThat(capability.getBody().canChangePassword()).isTrue();

    var wrong =
        restTemplate.exchange(
            url("/auth/password"),
            HttpMethod.PATCH,
            new HttpEntity<>(
                new PasswordChangeRequest("wrongPassword", "different456"), cookies(first)),
            String.class);

    assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(wrong.getBody()).contains("CURRENT_PASSWORD_INCORRECT");
    assertThat(me(second).getStatusCode()).isEqualTo(HttpStatus.OK);

    var changed =
        restTemplate.exchange(
            url("/auth/password"),
            HttpMethod.PATCH,
            new HttpEntity<>(
                new PasswordChangeRequest("password123", "different456"), cookies(first)),
            String.class);

    assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(me(first).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(me(second).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(refresh(second).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    assertThat(
            restTemplate
                .postForEntity(
                    url("/auth/login"), new LoginRequest("pwchange", "password123"), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);

    assertThat(
            restTemplate
                .postForEntity(
                    url("/auth/login"), new LoginRequest("pwchange", "different456"), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("로컬 자격 증명이 없는 회원은 capability false와 비밀번호 변경 403")
  void 소셜_계정_변경_거부() {
    Member member = testAuthHelper.createMember();
    TokenPair pair = testAuthHelper.issueTokens(member);

    var capability =
        restTemplate.exchange(
            url("/auth/me"), HttpMethod.GET, new HttpEntity<>(cookies(pair)), AuthResponse.class);

    assertThat(capability.getBody().canChangePassword()).isFalse();

    var response =
        restTemplate.exchange(
            url("/auth/password"),
            HttpMethod.PATCH,
            new HttpEntity<>(
                new PasswordChangeRequest("password123", "different456"), cookies(pair)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody()).contains("LOCAL_ACCOUNT_REQUIRED");
  }

  @Test
  @DisplayName("sid 없는 과거 JWT와 폐기된 갱신 토큰은 인증하지 않고 새 로그인을 허용한다")
  void 과거_토큰_거부_새로그인_허용() {
    Member member = testAuthHelper.createMember();
    TokenPair sessionTokens = testAuthHelper.issueTokens(member);
    TokenPair other = testAuthHelper.issueTokens(member);

    TokenPair legacy =
        new TokenPair(
            jwtProvider.createAccessToken(member.getPublicId(), "USER"),
            sessionTokens.refreshToken());

    assertThat(me(legacy).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    var oldRefresh =
        refreshTokenRepository
            .findByTokenHash(HashUtils.sha256Hex(sessionTokens.refreshToken()))
            .orElseThrow();
    oldRefresh.setRevoked(true);
    refreshTokenRepository.saveAndFlush(oldRefresh);

    assertThat(refresh(legacy).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(me(legacy).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(refresh(sessionTokens).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(me(other).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(me(authSessionService.issue(member.getId())).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("유효한 인증 쿠키가 붙은 공개 API 요청에서 인증 상태를 SQL 한 번으로 확인한다")
  void 인증_필터_SQL_단일조회() {
    TokenPair pair = testAuthHelper.issueTokens(testAuthHelper.createMember());

    var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);

    try {
      statistics.clear();
      assertThat(restTemplate.getForEntity(url("/ranking/today"), String.class).getStatusCode())
          .isEqualTo(HttpStatus.OK);
      long rankingStatements = statistics.getPrepareStatementCount();
      statistics.clear();

      assertThat(
              restTemplate
                  .exchange(
                      url("/ranking/today"),
                      HttpMethod.GET,
                      new HttpEntity<>(cookies(pair)),
                      String.class)
                  .getStatusCode())
          .isEqualTo(HttpStatus.OK);

      assertThat(statistics.getPrepareStatementCount()).isEqualTo(rankingStatements + 1);
    } finally {
      statistics.setStatisticsEnabled(false);
    }
  }

  @Test
  @DisplayName("동시 RT 회전과 모든 기기 로그아웃 후에는 회전으로 새로 발급한 AT도 사용할 수 없다")
  void 갱신_전체_로그아웃_경합() throws Exception {
    TokenPair pair = testAuthHelper.issueTokens(testAuthHelper.createMember());
    var barrier = new CyclicBarrier(2);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var rotated =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return refresh(pair);
              });

      var revoked =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return restTemplate.exchange(
                    url("/auth/logout-all"),
                    HttpMethod.POST,
                    new HttpEntity<>(cookies(pair)),
                    String.class);
              });

      var rotation = rotated.get(15, TimeUnit.SECONDS);

      assertThat(revoked.get(15, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(me(pair).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

      if (rotation.getStatusCode().is2xxSuccessful()) {
        String access =
            rotation.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("access_token="))
                .findFirst()
                .orElseThrow()
                .split(";", 2)[0]
                .substring("access_token=".length());

        assertThat(me(new TokenPair(access, pair.refreshToken())).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
      } else {
        assertThat(rotation.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
      }
    }
  }

  @Test
  @DisplayName("세션 만료 정리와 새 회원 토큰 발급 경합이 새 인증 상태를 삭제하지 않는다")
  void 만료정리_신규발급_경합() throws Exception {
    var barrier = new CyclicBarrier(2);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var issuance =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                var pairs = new ArrayList<TokenPair>();
                for (int i = 0; i < 5; i++) {
                  pairs.add(testAuthHelper.issueTokens(testAuthHelper.createMember()));
                }

                return pairs;
              });

      var cleanup =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                for (int i = 0; i < 5; i++) {
                  authSessionService.discardExpired();
                }

                return true;
              });

      var pairs = issuance.get(15, TimeUnit.SECONDS);

      assertThat(cleanup.get(15, TimeUnit.SECONDS)).isTrue();
      pairs.forEach(pair -> assertThat(me(pair).getStatusCode()).isEqualTo(HttpStatus.OK));
    }
  }

  @Test
  @DisplayName("오래된 회원 프로필 저장이 이미 폐기한 로그인 세션을 되살릴 수 없다")
  void 회원_저장후_세션_폐기_유지() {
    var member = testAuthHelper.createMember();
    TokenPair pair = testAuthHelper.issueTokens(member);
    var stale = memberRepository.findByPublicId(member.getPublicId()).orElseThrow();

    authSessionService.revokeAll(member.getId());

    stale.setNickname("이전상태저장");
    memberRepository.saveAndFlush(stale);

    assertThat(me(pair).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
