package com.gakkaweo.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gakkaweo.backend.auth.config.JwtProperties;
import com.gakkaweo.backend.auth.jwt.JwtProvider;
import com.gakkaweo.backend.auth.service.AuthSessionService;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.auth.repository.AuthSessionRepository;
import com.gakkaweo.backend.domain.auth.repository.AuthenticationState;
import com.gakkaweo.backend.domain.auth.repository.RefreshTokenRepository;
import com.gakkaweo.backend.domain.member.entity.MemberRole;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("DB 로그인 세션 인증 단위 테스트")
class AuthSessionAuthenticationTest {

  private static final Instant NOW = Instant.parse("2026-10-11T00:00:00Z");

  private final UUID sessionId = UUID.randomUUID();
  private final UUID publicId = UUID.randomUUID();

  private final AuthSessionRepository authSessionRepository = mock(AuthSessionRepository.class);
  private final AuthSessionService authSessionService =
      new AuthSessionService(
          mock(RefreshTokenRepository.class),
          authSessionRepository,
          mock(MemberRepository.class),
          mock(JwtProvider.class),
          mock(JwtProperties.class),
          Clock.fixed(NOW, ZoneOffset.UTC),
          mock(TransactionTemplate.class));

  private AuthenticationState activeState() {
    var state = mock(AuthenticationState.class);

    when(state.getPublicId()).thenReturn(publicId);
    when(state.getRole()).thenReturn(MemberRole.USER);
    when(state.getBanned()).thenReturn(false);
    when(state.getExpiresAt()).thenReturn(NOW.plusSeconds(60));

    when(authSessionRepository.findAuthenticationStateByPublicId(sessionId))
        .thenReturn(Optional.of(state));

    return state;
  }

  @Test
  @DisplayName("유효한 세션은 DB의 회원 식별자와 현재 권한으로 인증한다")
  void 유효세션_DB회원_권한_인증() {
    activeState();

    var details = authSessionService.authenticate(sessionId, publicId, "USER");

    assertThat(details.publicId()).isEqualTo(publicId);
    assertThat(details.role()).isEqualTo("USER");
    verify(authSessionRepository).findAuthenticationStateByPublicId(sessionId);
  }

  @Test
  @DisplayName("DB에 세션이 없으면 인증하지 않는다")
  void 세션_없음_인증거부() {
    when(authSessionRepository.findAuthenticationStateByPublicId(sessionId))
        .thenReturn(Optional.empty());

    assertThat(authSessionService.authenticate(sessionId, publicId, "USER")).isNull();
  }

  @ParameterizedTest(name = "{displayName} [{0}]")
  @EnumSource(InvalidState.class)
  @DisplayName("소유자·권한이 다르거나 세션이 폐기·만료됐으면 인증하지 않는다")
  void 세션_소유자_권한_폐기_만료_검사(InvalidState invalid) {
    var state = activeState();

    switch (invalid) {
      case OWNER -> when(state.getPublicId()).thenReturn(UUID.randomUUID());
      case ROLE -> when(state.getRole()).thenReturn(MemberRole.ADMIN);
      case REVOKED -> when(state.isRevoked()).thenReturn(true);
      case EXPIRED -> when(state.getExpiresAt()).thenReturn(NOW.minusSeconds(1));
      case EXPIRY_BOUNDARY -> when(state.getExpiresAt()).thenReturn(NOW);
    }

    assertThat(authSessionService.authenticate(sessionId, publicId, "USER")).isNull();
  }

  @Test
  @DisplayName("세션이 폐기됐어도 차단된 회원은 차단 오류로 거부한다")
  void 폐기세션_차단회원_거부() {
    var state = activeState();
    when(state.getBanned()).thenReturn(true);
    when(state.isRevoked()).thenReturn(true);

    assertThatThrownBy(() -> authSessionService.authenticate(sessionId, publicId, "USER"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            failure -> assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.MEMBER_BANNED));
  }

  enum InvalidState {
    OWNER,
    ROLE,
    REVOKED,
    EXPIRED,
    EXPIRY_BOUNDARY
  }
}
