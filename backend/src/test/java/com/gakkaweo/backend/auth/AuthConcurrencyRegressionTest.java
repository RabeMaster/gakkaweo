package com.gakkaweo.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.gakkaweo.backend.admin.service.AdminUserService;
import com.gakkaweo.backend.auth.dto.TokenPair;
import com.gakkaweo.backend.auth.jwt.JwtProvider;
import com.gakkaweo.backend.auth.service.AccountService;
import com.gakkaweo.backend.auth.service.AuthSessionService;
import com.gakkaweo.backend.auth.service.LocalAuthService;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.auth.entity.AuthSession;
import com.gakkaweo.backend.domain.auth.repository.AuthSessionRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.LocalAccountRepository;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.support.IntegrationTestBase;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DisplayName("인증 발급·갱신과 보안 변경 경합 테스트")
class AuthConcurrencyRegressionTest extends IntegrationTestBase {

  private static final String PASSWORD = "PasswordFixture123";
  private static final String NEW_PASSWORD = "ChangedFixture456";

  @Autowired LocalAuthService localAuthService;
  @Autowired AuthSessionService authSessionService;
  @Autowired AccountService accountService;
  @Autowired AdminUserService adminUserService;
  @MockitoSpyBean JwtProvider jwtProvider;
  @MockitoSpyBean AuthSessionRepository authSessionRepository;
  @MockitoSpyBean PasswordEncoder passwordEncoder;
  @Autowired LocalAccountRepository localAccountRepository;
  @Autowired MemberRepository memberRepository;
  @Autowired MeterRegistry meterRegistry;

  @Test
  @DisplayName("로그인 커밋 실패는 세션과 성공 지표를 남기지 않고 재시도할 수 있다")
  void 로그인_커밋실패_성공지표_미기록_재시도() {
    String username = testAuthHelper.nextUsername();
    Member member = localAuthService.registerAndLogin(username, PASSWORD).member();

    var successes =
        meterRegistry.counter("auth.login.total", "provider", "local", "result", "success");
    double before = successes.count();
    long sessionCount = authSessionRepository.count();

    doAnswer(
            invocation -> {
              TransactionSynchronizationManager.registerSynchronization(
                  new TransactionSynchronization() {
                    @Override
                    public void beforeCommit(boolean readOnly) {
                      throw new IllegalStateException("commit fixture");
                    }
                  });
              return invocation.callRealMethod();
            })
        .when(jwtProvider)
        .createAccessToken(any(UUID.class), anyString(), any(UUID.class));

    assertThatThrownBy(() -> localAuthService.login(username, PASSWORD))
        .isInstanceOf(IllegalStateException.class);

    assertThat(successes.count()).isEqualTo(before);
    assertThat(authSessionRepository.count()).isEqualTo(sessionCount);

    reset(jwtProvider);
    var loggedIn = localAuthService.login(username, PASSWORD);

    assertThat(loggedIn.member().getId()).isEqualTo(member.getId());
    assertThat(successes.count()).isEqualTo(before + 1);
    assertThat(authSessionRepository.count()).isEqualTo(sessionCount + 1);
  }

  @Test
  @DisplayName("세션 저장 실패는 회원가입도 롤백하며 재시도할 수 있다")
  void 세션_저장실패_가입_롤백_재시도() {
    String username = testAuthHelper.nextUsername();
    long memberCount = memberRepository.count();

    doThrow(new DataIntegrityViolationException("DB fixture"))
        .when(authSessionRepository)
        .save(any(AuthSession.class));

    assertThatThrownBy(() -> localAuthService.registerAndLogin(username, PASSWORD))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(localAccountRepository.existsByUsernameIgnoreCase(username)).isFalse();
    assertThat(memberRepository.count()).isEqualTo(memberCount);

    reset(authSessionRepository);

    assertThat(localAuthService.registerAndLogin(username, PASSWORD).tokens()).isNotNull();
  }

  private void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  private void mutate(Mutation mutation, Member member, Member admin) {
    switch (mutation) {
      case LOGOUT_ALL -> authSessionService.revokeAll(member.getId());
      case PASSWORD ->
          localAuthService.changePassword(member.getPublicId(), PASSWORD, NEW_PASSWORD);
      case BAN -> adminUserService.banUser(member.getPublicId(), admin.getPublicId(), "127.0.0.1");
      case DELETE -> accountService.deleteAccount(member.getPublicId());
    }
  }

  private ErrorCode expectedError(Mutation mutation) {
    return switch (mutation) {
      case BAN -> ErrorCode.MEMBER_BANNED;
      case DELETE -> ErrorCode.MEMBER_NOT_FOUND;
      default -> ErrorCode.CONCURRENT_MODIFICATION;
    };
  }

  private void assertInvalid(TokenPair tokens, Mutation mutation) {
    var headers = testAuthHelper.refreshCookieHeaderFor(tokens.refreshToken());
    headers.set("Cookie", "access_token=" + tokens.accessToken());

    assertThat(
            restTemplate
                .exchange(url("/auth/me"), HttpMethod.GET, new HttpEntity<>(headers), String.class)
                .getStatusCode())
        .isEqualTo(mutation == Mutation.BAN ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED);

    String sid = jwtProvider.parseAccessToken(tokens.accessToken()).get("sid", String.class);
    var session = authSessionRepository.findByPublicId(UUID.fromString(sid));

    assertThat(session.isEmpty() || session.get().isRevoked()).isTrue();

    assertThatThrownBy(() -> authSessionService.rotate(tokens.refreshToken()))
        .isInstanceOf(BusinessException.class);
  }

  // 실제 DB commit 뒤 응답을 지연시켜, 이후 폐기가 늦은 응답에도 적용되는지 확인합니다.
  private void pausePublication(CountDownLatch committed, CountDownLatch release) {
    AtomicBoolean first = new AtomicBoolean(true);

    doAnswer(
            invocation -> {
              if (Thread.currentThread().getName().equals("delayed-auth-publication")
                  && first.compareAndSet(true, false)) {
                TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                      @Override
                      public void afterCommit() {
                        committed.countDown();
                        await(release);
                      }
                    });
              }

              return invocation.callRealMethod();
            })
        .when(jwtProvider)
        .createAccessToken(any(UUID.class), anyString(), any(UUID.class));
  }

  @ParameterizedTest(name = "{displayName} [{0}]")
  @EnumSource(Mutation.class)
  @DisplayName("토큰 발급 응답이 늦어도 이후 보안 변경과 세션 폐기는 유지된다")
  void 발급_응답지연_보안변경_유지(Mutation mutation) throws Exception {
    verifyDelayedPublication(mutation, false);
  }

  @ParameterizedTest(name = "{displayName} [{0}]")
  @EnumSource(Mutation.class)
  @DisplayName("토큰 갱신 응답이 늦어도 이후 보안 변경과 세션 폐기는 유지된다")
  void 갱신_응답지연_보안변경_유지(Mutation mutation) throws Exception {
    verifyDelayedPublication(mutation, true);
  }

  private void verifyDelayedPublication(Mutation mutation, boolean rotation) throws Exception {
    Member admin = testAuthHelper.createSuperAdmin();
    Member member =
        localAuthService.registerAndLogin(testAuthHelper.nextUsername(), PASSWORD).member();
    TokenPair initial = testAuthHelper.issueTokens(member);

    CountDownLatch committed = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    pausePublication(committed, release);

    try (var executor =
        Executors.newSingleThreadExecutor(r -> new Thread(r, "delayed-auth-publication"))) {
      var pending =
          executor.submit(
              () ->
                  rotation
                      ? authSessionService.rotate(initial.refreshToken())
                      : authSessionService.issue(member.getId()));

      try {
        await(committed);
        mutate(mutation, member, admin);
      } finally {
        release.countDown();
      }

      TokenPair delayed = pending.get(15, TimeUnit.SECONDS);

      assertInvalid(delayed, mutation);
      assertInvalid(initial, mutation);
      assertInvalid(delayed, mutation);
    }
  }

  @ParameterizedTest(name = "{displayName} [{0}]")
  @EnumSource(PasswordMutation.class)
  @DisplayName("비밀번호 검증 후 보안 상태가 바뀌면 토큰 발급 전에 다시 검사한다")
  void 로그인_검증후_보안변경_재검사(PasswordMutation mutation) throws Exception {
    Member admin = testAuthHelper.createSuperAdmin();
    String username = testAuthHelper.nextUsername();
    Member member = localAuthService.registerAndLogin(username, PASSWORD).member();

    CountDownLatch checked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              Object result = invocation.callRealMethod();
              if (Thread.currentThread().getName().equals("delayed-local-login")) {
                checked.countDown();
                await(release);
              }

              return result;
            })
        .when(passwordEncoder)
        .matches(anyString(), anyString());

    try (var executor =
        Executors.newSingleThreadExecutor(r -> new Thread(r, "delayed-local-login"))) {
      var pending =
          executor.submit(
              () -> {
                try {
                  localAuthService.login(username, PASSWORD);
                  return null;
                } catch (BusinessException failure) {
                  return failure.getErrorCode();
                }
              });

      try {
        await(checked);
        mutate(Mutation.valueOf(mutation.name()), member, admin);
      } finally {
        release.countDown();
      }

      assertThat(pending.get(15, TimeUnit.SECONDS))
          .isEqualTo(expectedError(Mutation.valueOf(mutation.name())));
    }
  }

  @ParameterizedTest(name = "{displayName} [{0}]")
  @EnumSource(PasswordMutation.class)
  @DisplayName("비밀번호 변경은 잠금 후 최신 비밀번호와 보안 상태를 다시 검사한다")
  void 비밀번호_변경전_보안변경_재검사(PasswordMutation mutation) throws Exception {
    Member admin = testAuthHelper.createSuperAdmin();
    String username = testAuthHelper.nextUsername();
    Member member = localAuthService.registerAndLogin(username, PASSWORD).member();

    CountDownLatch checked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              Object hash = invocation.callRealMethod();
              if (Thread.currentThread().getName().equals("delayed-password-write")) {
                checked.countDown();
                await(release);
              }

              return hash;
            })
        .when(passwordEncoder)
        .encode(anyString());

    try (var executor =
        Executors.newSingleThreadExecutor(r -> new Thread(r, "delayed-password-write"))) {
      var pending =
          executor.submit(
              () -> {
                try {
                  localAuthService.changePassword(
                      member.getPublicId(), PASSWORD, "LosingFixture789");
                  return null;
                } catch (BusinessException failure) {
                  return failure.getErrorCode();
                }
              });

      try {
        await(checked);
        mutate(Mutation.valueOf(mutation.name()), member, admin);
      } finally {
        release.countDown();
      }

      assertThat(pending.get(15, TimeUnit.SECONDS))
          .isEqualTo(expectedError(Mutation.valueOf(mutation.name())));

      if (mutation == PasswordMutation.PASSWORD) {
        assertThat(localAuthService.login(username, NEW_PASSWORD)).isNotNull();
        assertThatThrownBy(() -> localAuthService.login(username, "LosingFixture789"))
            .isInstanceOf(BusinessException.class);
      }
    }
  }

  enum Mutation {
    LOGOUT_ALL,
    PASSWORD,
    BAN,
    DELETE
  }

  enum PasswordMutation {
    PASSWORD,
    BAN,
    DELETE
  }
}
