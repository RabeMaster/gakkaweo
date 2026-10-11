package com.gakkaweo.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gakkaweo.backend.admin.dto.RoleChangeRequest;
import com.gakkaweo.backend.admin.service.AdminUserService;
import com.gakkaweo.backend.auth.dto.TokenPair;
import com.gakkaweo.backend.auth.service.AccountService;
import com.gakkaweo.backend.auth.service.AuthSessionService;
import com.gakkaweo.backend.auth.service.LocalAuthService;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("계정 보안 변경 및 롤백 회귀 테스트")
class AccountSecurityRegressionTest extends IntegrationTestBase {

  @Autowired AdminUserService adminUserService;
  @Autowired AccountService accountService;
  @Autowired LocalAuthService localAuthService;
  @Autowired AuthSessionService authSessionService;
  @Autowired StringRedisTemplate redisTemplate;
  @Autowired MemberRepository memberRepository;
  @Autowired TransactionTemplate transactionTemplate;

  private void clearRedis() {
    try (var connection = redisTemplate.getConnectionFactory().getConnection()) {
      connection.serverCommands().flushAll();
    }
  }

  private HttpStatus me(TokenPair tokens) {
    var headers = testAuthHelper.refreshCookieHeaderFor(tokens.refreshToken());
    headers.set(
        "Cookie",
        "access_token=" + tokens.accessToken() + "; refresh_token=" + tokens.refreshToken());

    return HttpStatus.valueOf(
        restTemplate
            .exchange(url("/auth/me"), HttpMethod.GET, new HttpEntity<>(headers), String.class)
            .getStatusCode()
            .value());
  }

  private void assertRevoked(TokenPair tokens) {
    assertThat(me(tokens)).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThatThrownBy(() -> authSessionService.rotate(tokens.refreshToken()))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("권한 변경 후 DB를 다시 조회해도 모든 기존 기기는 폐기된다")
  void 권한_변경_기존기기_폐기() {
    var admin = testAuthHelper.createSuperAdmin();
    var member = testAuthHelper.createAdmin();
    var first = testAuthHelper.issueTokens(member);
    var second = testAuthHelper.issueTokens(member);

    adminUserService.changeRole(
        member.getPublicId(), admin.getPublicId(), new RoleChangeRequest("USER"), "127.0.0.1");
    clearRedis();

    assertRevoked(first);
    assertRevoked(second);

    var fresh = memberRepository.findByPublicId(member.getPublicId()).orElseThrow();

    assertThat(me(testAuthHelper.issueTokens(fresh))).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("차단을 해제해도 이전에 폐기된 로그인은 다시 유효해지지 않는다")
  void 차단_해제_폐기기기_복구불가() {
    var admin = testAuthHelper.createSuperAdmin();
    var member = testAuthHelper.createMember();
    var tokens = testAuthHelper.issueTokens(member);

    adminUserService.banUser(member.getPublicId(), admin.getPublicId(), "127.0.0.1");

    assertThat(me(tokens)).isEqualTo(HttpStatus.FORBIDDEN);

    adminUserService.unbanUser(member.getPublicId(), admin.getPublicId(), "127.0.0.1");
    clearRedis();

    assertRevoked(tokens);
  }

  @Test
  @DisplayName("회원 탈퇴 후 모든 기기의 인증과 갱신은 거부된다")
  void 회원_탈퇴_모든기기_폐기() {
    var member = testAuthHelper.createMember();
    var first = testAuthHelper.issueTokens(member);
    var second = testAuthHelper.issueTokens(member);

    accountService.deleteAccount(member.getPublicId());
    clearRedis();

    assertRevoked(first);
    assertRevoked(second);
  }

  @Test
  @DisplayName("관리자 강제 탈퇴 후 모든 기기의 인증과 갱신은 거부된다")
  void 관리자_탈퇴_모든기기_폐기() {
    var admin = testAuthHelper.createSuperAdmin();
    var member = testAuthHelper.createMember();
    var tokens = testAuthHelper.issueTokens(member);

    adminUserService.forceDeleteUser(member.getPublicId(), admin.getPublicId(), "127.0.0.1");
    clearRedis();

    assertRevoked(tokens);
  }

  @Test
  @DisplayName("세션 폐기가 롤백되면 기존 액세스 토큰과 갱신 토큰은 유효하다")
  void 세션_폐기_롤백_기존토큰_유지() {
    var member = testAuthHelper.createMember();
    var tokens = testAuthHelper.issueTokens(member);

    assertThatThrownBy(
            () ->
                transactionTemplate.executeWithoutResult(
                    status -> {
                      var locked =
                          memberRepository
                              .findByPublicIdForUpdate(member.getPublicId())
                              .orElseThrow();

                      authSessionService.revokeAll(locked.getId());
                      throw new IllegalStateException("rollback fixture");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(me(tokens)).isEqualTo(HttpStatus.OK);
    assertThat(me(authSessionService.rotate(tokens.refreshToken()))).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("차단 변경이 롤백되면 기존 인증과 갱신 요청은 유효하다")
  void 차단_롤백_기존인증_갱신_유지() {
    var member = testAuthHelper.createMember();
    var tokens = testAuthHelper.issueTokens(member);

    assertThatThrownBy(
            () ->
                transactionTemplate.executeWithoutResult(
                    status -> {
                      var locked =
                          memberRepository
                              .findByPublicIdForUpdate(member.getPublicId())
                              .orElseThrow();

                      locked.setBanned(true);
                      authSessionService.revokeAll(locked.getId());
                      throw new IllegalStateException("rollback fixture");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(me(tokens)).isEqualTo(HttpStatus.OK);

    var headers = testAuthHelper.refreshCookieHeaderFor(tokens.refreshToken());
    headers.add("Cookie", "access_token=" + tokens.accessToken());

    assertThat(
            restTemplate
                .exchange(
                    url("/auth/refresh"), HttpMethod.POST, new HttpEntity<>(headers), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(memberRepository.findByPublicId(member.getPublicId()).orElseThrow().getBanned())
        .isFalse();
    assertThat(me(tokens)).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("비밀번호 변경이 롤백되면 기존 비밀번호와 모든 세션은 유지된다")
  void 비밀번호_변경_롤백_기존비밀번호_세션_유지() {
    var member = localAuthService.registerAndLogin("rollbackpassword", "password123").member();
    var first = testAuthHelper.issueTokens(member);
    var second = testAuthHelper.issueTokens(member);

    assertThatThrownBy(
            () ->
                transactionTemplate.executeWithoutResult(
                    status -> {
                      localAuthService.changePassword(
                          member.getPublicId(), "password123", "different456");
                      throw new IllegalStateException("rollback fixture");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(me(first)).isEqualTo(HttpStatus.OK);
    assertThat(me(second)).isEqualTo(HttpStatus.OK);
    assertThat(localAuthService.login("rollbackpassword", "password123")).isNotNull();
    assertThatThrownBy(() -> localAuthService.login("rollbackpassword", "different456"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("백엔드는 UTF-8 바이트 제한을 검사하고 비밀번호 공백은 보존한다")
  void 비밀번호_바이트경계_공백_보존() {
    String valid = "😀".repeat(18);
    var member = localAuthService.registerAndLogin("unicodebytes", valid).member();

    assertThat(localAuthService.login("unicodebytes", valid).member().getId())
        .isEqualTo(member.getId());
    assertThatThrownBy(() -> localAuthService.registerAndLogin("toolongbytes", valid + "a"))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> localAuthService.login("unicodebytes", valid + "a"))
        .isInstanceOf(BusinessException.class);

    localAuthService.changePassword(member.getPublicId(), valid, " spaced password ");

    assertThat(localAuthService.login("unicodebytes", " spaced password ")).isNotNull();
    assertThatThrownBy(() -> localAuthService.login("unicodebytes", "spaced password"))
        .isInstanceOf(BusinessException.class);
  }
}
