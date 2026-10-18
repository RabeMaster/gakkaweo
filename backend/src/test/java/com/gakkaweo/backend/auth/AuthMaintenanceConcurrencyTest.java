package com.gakkaweo.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.gakkaweo.backend.admin.service.AdminUserService;
import com.gakkaweo.backend.auth.dto.TokenPair;
import com.gakkaweo.backend.auth.jwt.JwtProvider;
import com.gakkaweo.backend.auth.service.AccountService;
import com.gakkaweo.backend.auth.service.AuthSessionService;
import com.gakkaweo.backend.auth.service.LocalAuthService;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.common.util.HashUtils;
import com.gakkaweo.backend.domain.auth.entity.RefreshToken;
import com.gakkaweo.backend.domain.auth.repository.AuthSessionRepository;
import com.gakkaweo.backend.domain.auth.repository.RefreshTokenRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("실제 만료 행의 정리와 인증 쓰기 경합")
class AuthMaintenanceConcurrencyTest extends IntegrationTestBase {

  private static final String PASSWORD = "PasswordFixture123";
  private static final String NEW_PASSWORD = "ChangedFixture456";

  @Autowired AuthSessionService authSessionService;
  @Autowired LocalAuthService localAuthService;
  @Autowired AdminUserService adminUserService;
  @Autowired AccountService accountService;
  @Autowired AuthSessionRepository authSessionRepository;
  @Autowired RefreshTokenRepository refreshTokenRepository;
  @Autowired MemberRepository memberRepository;
  @Autowired JwtProvider jwtProvider;
  @Autowired JdbcTemplate jdbcTemplate;

  private UUID sid(TokenPair pair) {
    return UUID.fromString(
        jwtProvider.parseAccessToken(pair.accessToken()).get("sid", String.class));
  }

  private Fixture fixture() {
    String username = testAuthHelper.nextUsername();
    var registration = localAuthService.registerAndLogin(username, PASSWORD);
    var active = authSessionService.issue(registration.member().getId());

    Timestamp expiredAt = Timestamp.from(clock.instant().minusSeconds(60));
    jdbcTemplate.update(
        "UPDATE auth_sessions SET expires_at = ? WHERE public_id = ?",
        expiredAt,
        sid(registration.tokens()));
    jdbcTemplate.update(
        "UPDATE refresh_tokens SET expires_at = ? WHERE token_hash = ?",
        expiredAt,
        HashUtils.sha256Hex(registration.tokens().refreshToken()));

    return new Fixture(
        registration.member(),
        testAuthHelper.createSuperAdmin().getPublicId(),
        username,
        registration.tokens(),
        active);
  }

  private Object perform(Action action, Fixture fixture) {
    UUID publicId = fixture.member().getPublicId();

    try {
      return switch (action) {
        case ROTATE_EXPIRED -> authSessionService.rotate(fixture.expired().refreshToken());
        case ROTATE_ACTIVE -> authSessionService.rotate(fixture.active().refreshToken());
        case LOGOUT -> {
          authSessionService.logout(
              fixture.expired().accessToken(), fixture.expired().refreshToken());
          yield null;
        }
        case LOGOUT_ALL -> {
          authSessionService.revokeAll(fixture.member().getId());
          yield null;
        }
        case PASSWORD -> {
          localAuthService.changePassword(publicId, PASSWORD, NEW_PASSWORD);
          yield null;
        }
        case BAN -> {
          adminUserService.banUser(publicId, fixture.adminId(), "127.0.0.1");
          yield null;
        }
        case DELETE -> {
          accountService.deleteAccount(publicId);
          yield null;
        }
      };
    } catch (BusinessException failure) {
      return failure.getErrorCode();
    }
  }

  private void awaitLatch(CountDownLatch latch) throws InterruptedException {
    assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue();
  }

  private void awaitDatabaseLockWait() {
    // PostgreSQL에서 실제 잠금 대기를 확인한 뒤 선행 작업을 풀어 줍니다.
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(50))
        .untilAsserted(
            () ->
                assertThat(
                        jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM pg_stat_activity"
                                + " WHERE datname = current_database() AND wait_event_type = 'Lock'",
                            Integer.class))
                    .isPositive());
  }

  private void race(Action action, boolean actionFirst) throws Exception {
    Fixture fixture = fixture();
    var paused = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var first = new AtomicBoolean(true);

    boolean locksSession =
        action == Action.ROTATE_EXPIRED
            || action == Action.ROTATE_ACTIVE
            || action == Action.LOGOUT;

    // 테스트 AOP는 실제 저장소 호출을 그대로 수행하고, 잠금을 얻은 뒤에만 지연합니다.
    MethodInterceptor actionAdvice =
        invocation -> {
          Object result = invocation.proceed();
          String pauseMethod =
              locksSession && action == Action.LOGOUT
                  ? "findByPublicIdForUpdate"
                  : "findByIdForUpdate";

          if (Thread.currentThread().getName().equals("maintenance-auth-action")
              && (invocation.getMethod().getName().equals(pauseMethod)
                  || (!locksSession
                      && invocation.getMethod().getName().equals("findByPublicIdForUpdate")))
              && first.compareAndSet(true, false)) {
            paused.countDown();
            awaitLatch(release);
          }

          return result;
        };

    MethodInterceptor cleanupAdvice =
        invocation -> {
          Object result = invocation.proceed();

          if (Thread.currentThread().getName().equals("maintenance-cleanup")
              && invocation.getMethod().getName().equals("deleteExpiredByMemberId")
              && first.compareAndSet(true, false)) {
            paused.countDown();
            awaitLatch(release);
          }

          return result;
        };

    Advised proxy =
        (Advised)
            (actionFirst
                ? (locksSession ? authSessionRepository : memberRepository)
                : refreshTokenRepository);
    MethodInterceptor advice = actionFirst ? actionAdvice : cleanupAdvice;
    proxy.addAdvice(0, advice);

    Object result;

    try (var actionExecutor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "maintenance-auth-action"));
        var cleanupExecutor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "maintenance-cleanup"))) {
      try {
        if (actionFirst) {
          var actionResult = actionExecutor.submit(() -> perform(action, fixture));
          awaitLatch(paused);

          var cleanup = cleanupExecutor.submit(authSessionService::discardExpired);
          awaitDatabaseLockWait();

          release.countDown();
          result = actionResult.get(15, TimeUnit.SECONDS);
          cleanup.get(15, TimeUnit.SECONDS);
        } else {
          var cleanup = cleanupExecutor.submit(authSessionService::discardExpired);
          awaitLatch(paused);

          var actionResult = actionExecutor.submit(() -> perform(action, fixture));
          awaitDatabaseLockWait();

          release.countDown();
          cleanup.get(15, TimeUnit.SECONDS);
          result = actionResult.get(15, TimeUnit.SECONDS);
        }
      } finally {
        release.countDown();
      }
    } finally {
      proxy.removeAdvice(advice);
    }

    if (action == Action.ROTATE_EXPIRED) {
      assertThat(result)
          .isEqualTo(actionFirst ? ErrorCode.EXPIRED_TOKEN : ErrorCode.INVALID_REFRESH_TOKEN);
    } else if (action == Action.ROTATE_ACTIVE) {
      assertThat(result).isInstanceOf(TokenPair.class);
      assertThat(
              authSessionService.authenticate(
                  sid((TokenPair) result), fixture.member().getPublicId(), "USER"))
          .isNotNull();
    } else {
      assertThat(result).isNull();
    }

    assertThat(authSessionRepository.findByPublicId(sid(fixture.expired()))).isEmpty();
    assertThat(
            refreshTokenRepository.findByTokenHash(
                HashUtils.sha256Hex(fixture.expired().refreshToken())))
        .isEmpty();

    var active = authSessionRepository.findByPublicId(sid(fixture.active()));

    if (action == Action.DELETE) {
      assertThat(active).isEmpty();
      assertThat(memberRepository.findById(fixture.member().getId())).isEmpty();
      assertThat(refreshTokenRepository.findByMemberId(fixture.member().getId())).isEmpty();
    } else {
      assertThat(active).isPresent();

      boolean revoked =
          action == Action.LOGOUT_ALL || action == Action.PASSWORD || action == Action.BAN;

      assertThat(active.orElseThrow().isRevoked()).isEqualTo(revoked);

      var sessionTokens = refreshTokenRepository.findBySessionId(active.orElseThrow().getId());

      assertThat(sessionTokens).isNotEmpty();

      if (action == Action.ROTATE_ACTIVE) {
        assertThat(sessionTokens).anyMatch(token -> !token.isRevoked());
        assertThat(
                refreshTokenRepository
                    .findByTokenHash(HashUtils.sha256Hex(fixture.active().refreshToken()))
                    .orElseThrow()
                    .isRevoked())
            .isTrue();
      } else {
        assertThat(sessionTokens).allMatch(token -> token.isRevoked() == revoked);
      }
    }

    if (action == Action.PASSWORD) {
      assertThat(localAuthService.login(fixture.username(), NEW_PASSWORD)).isNotNull();
      assertThatThrownBy(() -> localAuthService.login(fixture.username(), PASSWORD))
          .isInstanceOf(BusinessException.class);
    }

    if (action == Action.BAN) {
      assertThat(memberRepository.findById(fixture.member().getId()).orElseThrow().getBanned())
          .isTrue();
    }
  }

  @ParameterizedTest(name = "{displayName} [{0}]")
  @EnumSource(Action.class)
  @DisplayName("인증 쓰기가 먼저 잠그면 만료 정리는 DB 잠금을 기다린다")
  void 인증쓰기_선행_만료정리_잠금대기(Action action) throws Exception {
    race(action, true);
  }

  @ParameterizedTest(name = "{displayName} [{0}]")
  @EnumSource(Action.class)
  @DisplayName("만료 정리가 먼저 잠그면 인증 쓰기는 DB 잠금을 기다린다")
  void 만료정리_선행_인증쓰기_잠금대기(Action action) throws Exception {
    race(action, false);
  }

  @Test
  @DisplayName("만료된 갱신 토큰을 정리해도 유효한 세션은 보존된다")
  void 만료토큰_정리_유효세션_보존() {
    Member member = testAuthHelper.createMember();
    TokenPair original = authSessionService.issue(member.getId());
    TokenPair rotated = authSessionService.rotate(original.refreshToken());

    jdbcTemplate.update(
        "UPDATE refresh_tokens SET expires_at = ? WHERE token_hash = ?",
        Timestamp.from(clock.instant().minusSeconds(60)),
        HashUtils.sha256Hex(original.refreshToken()));

    authSessionService.discardExpired();

    assertThat(refreshTokenRepository.findByTokenHash(HashUtils.sha256Hex(original.refreshToken())))
        .isEmpty();
    assertThat(refreshTokenRepository.findByTokenHash(HashUtils.sha256Hex(rotated.refreshToken())))
        .isPresent();
    assertThat(authSessionService.authenticate(sid(rotated), member.getPublicId(), "USER"))
        .isNotNull();
  }

  @Test
  @DisplayName("토큰 없는 만료 세션과 세션 없는 이전 토큰도 정리한다")
  void 토큰없는_만료세션_세션없는_이전토큰_정리() {
    Member first = testAuthHelper.createMember();
    TokenPair pair = authSessionService.issue(first.getId());

    refreshTokenRepository.deleteById(
        refreshTokenRepository
            .findByTokenHash(HashUtils.sha256Hex(pair.refreshToken()))
            .orElseThrow()
            .getId());
    jdbcTemplate.update(
        "UPDATE auth_sessions SET expires_at = ? WHERE public_id = ?",
        Timestamp.from(clock.instant().minusSeconds(60)),
        sid(pair));

    Member second = testAuthHelper.createMember();
    refreshTokenRepository.saveAndFlush(
        new RefreshToken(
            second,
            HashUtils.sha256Hex("legacy fixture"),
            UUID.randomUUID(),
            clock.instant().minusSeconds(60)));

    authSessionService.discardExpired();

    assertThat(authSessionRepository.findByMemberId(first.getId())).isEmpty();
    assertThat(refreshTokenRepository.findByMemberId(second.getId())).isEmpty();
    assertThat(memberRepository.findById(first.getId())).isPresent();
    assertThat(memberRepository.findById(second.getId())).isPresent();
  }

  @Test
  @DisplayName("세션 삭제가 실패하면 해당 회원의 토큰 삭제도 롤백되고 재시도할 수 있다")
  void 세션삭제_실패_회원별_토큰삭제_롤백() {
    Fixture fixture = fixture();

    MethodInterceptor failure =
        invocation -> {
          Object result = invocation.proceed();
          if (invocation.getMethod().getName().equals("deleteExpiredByMemberId")) {
            throw new IllegalStateException("rollback fixture");
          }

          return result;
        };

    Advised proxy = (Advised) authSessionRepository;
    proxy.addAdvice(0, failure);

    try {
      assertThatThrownBy(authSessionService::discardExpired)
          .isInstanceOf(IllegalStateException.class);
    } finally {
      proxy.removeAdvice(failure);
    }

    assertThat(authSessionRepository.findByPublicId(sid(fixture.expired()))).isPresent();
    assertThat(
            refreshTokenRepository.findByTokenHash(
                HashUtils.sha256Hex(fixture.expired().refreshToken())))
        .isPresent();

    authSessionService.discardExpired();

    assertThat(authSessionRepository.findByPublicId(sid(fixture.expired()))).isEmpty();
    assertThat(
            refreshTokenRepository.findByTokenHash(
                HashUtils.sha256Hex(fixture.expired().refreshToken())))
        .isEmpty();
    assertThat(
            authSessionService.authenticate(
                sid(fixture.active()), fixture.member().getPublicId(), "USER"))
        .isNotNull();
  }

  enum Action {
    ROTATE_EXPIRED,
    ROTATE_ACTIVE,
    LOGOUT,
    LOGOUT_ALL,
    PASSWORD,
    BAN,
    DELETE
  }

  private record Fixture(
      Member member, UUID adminId, String username, TokenPair expired, TokenPair active) {}
}
