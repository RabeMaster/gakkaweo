package com.gakkaweo.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gakkaweo.backend.auth.config.JwtProperties;
import com.gakkaweo.backend.auth.jwt.JwtAuthenticationFilter;
import com.gakkaweo.backend.auth.jwt.JwtProvider;
import com.gakkaweo.backend.auth.service.AuthSessionService;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.CannotCreateTransactionException;

@DisplayName("JWT 인증 필터 오류 처리 단위 테스트")
class JwtAuthenticationFilterTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest(name = "{displayName} [{0}]")
  @ValueSource(booleans = {false, true})
  @DisplayName("DB 또는 트랜잭션 오류는 503을 반환하고 인증과 필터 진행을 중단한다")
  void DB조회_실패_503_인증거부(boolean transactionFailure) throws Exception {
    Clock clock = Clock.fixed(Instant.parse("2026-10-11T00:00:00Z"), ZoneOffset.UTC);
    var jwtProvider =
        new JwtProvider(
            new JwtProperties(
                Base64.getEncoder().encodeToString(new byte[32]),
                Duration.ofMinutes(30),
                Duration.ofDays(7)),
            clock);

    var authSessionService = mock(AuthSessionService.class);
    RuntimeException failure =
        transactionFailure
            ? new CannotCreateTransactionException("isolated DB fixture")
            : new DataAccessResourceFailureException("isolated DB fixture");
    when(authSessionService.authenticate(any(UUID.class), any(UUID.class), eq("USER")))
        .thenThrow(failure);

    var filter =
        new JwtAuthenticationFilter(jwtProvider, authSessionService, new ObjectMapper(), clock);
    var request = new MockHttpServletRequest("GET", "/auth/me");
    request.setCookies(
        new Cookie(
            "access_token",
            jwtProvider.createAccessToken(UUID.randomUUID(), "USER", UUID.randomUUID())));
    var response = new MockHttpServletResponse();
    var chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getContentAsString()).contains("AUTH_SERVICE_UNAVAILABLE");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(chain.getRequest()).isNull();
  }
}
