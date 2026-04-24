package com.gakkaweo.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gakkaweo.backend.auth.config.CookieProperties;
import com.gakkaweo.backend.auth.config.JwtProperties;
import com.gakkaweo.backend.auth.config.OAuth2Properties;
import com.gakkaweo.backend.auth.dto.TokenPair;
import com.gakkaweo.backend.auth.oauth2.CookieAuthorizationRequestRepository;
import com.gakkaweo.backend.auth.oauth2.CustomOAuth2User;
import com.gakkaweo.backend.auth.oauth2.handler.OAuth2LoginFailureHandler;
import com.gakkaweo.backend.auth.oauth2.handler.OAuth2LoginSuccessHandler;
import com.gakkaweo.backend.auth.service.AuthService;
import com.gakkaweo.backend.auth.util.CookieUtils;
import com.gakkaweo.backend.domain.member.entity.Member;
import java.net.HttpCookie;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

@DisplayName("OAuth2 로그인 Handler 단위 테스트")
class OAuth2LoginHandlerTest {

  private static final String REDIRECT_URI = "https://gakkaweo.example/oauth/callback";

  private AuthService authService;
  private CookieUtils cookieUtils;
  private OAuth2Properties oAuth2Properties;
  private CookieAuthorizationRequestRepository authorizationRequestRepository;

  private static List<HttpCookie> allSetCookies(MockHttpServletResponse response) {
    return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
        .flatMap(header -> HttpCookie.parse(header).stream())
        .toList();
  }

  private static HttpCookie findCookie(List<HttpCookie> cookies, String name) {
    return cookies.stream().filter(c -> name.equals(c.getName())).findFirst().orElse(null);
  }

  @BeforeEach
  void setUp() {
    authService = Mockito.mock(AuthService.class);
    JwtProperties jwtProperties =
        new JwtProperties(
            "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
            Duration.ofMinutes(30),
            Duration.ofDays(14));
    CookieProperties cookieProperties = new CookieProperties(true, "");
    cookieUtils = new CookieUtils(jwtProperties, cookieProperties);
    oAuth2Properties = new OAuth2Properties(REDIRECT_URI);
    authorizationRequestRepository =
        new CookieAuthorizationRequestRepository(cookieProperties, new ObjectMapper());
  }

  private static class NullMessageAuthenticationException extends AuthenticationException {
    NullMessageAuthenticationException() {
      super(null);
    }
  }

  @Nested
  @DisplayName("SuccessHandler")
  class SuccessHandler {

    @Test
    @DisplayName("인증 성공 - access/refresh/has_session 3종 쿠키 + 리다이렉트")
    void 성공_쿠키_리다이렉트() throws Exception {
      OAuth2LoginSuccessHandler handler =
          new OAuth2LoginSuccessHandler(
              authService, cookieUtils, oAuth2Properties, authorizationRequestRepository);
      Member member = new Member("tester");
      when(authService.issueTokens(any(Member.class)))
          .thenReturn(new TokenPair("access-value", "refresh-value"));

      Authentication authentication =
          new UsernamePasswordAuthenticationToken(
              new CustomOAuth2User(member, Map.of("id", "1")), null);
      MockHttpServletResponse response = new MockHttpServletResponse();

      handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

      List<HttpCookie> setCookies = allSetCookies(response);
      assertThat(setCookies)
          .extracting(HttpCookie::getName)
          .contains("access_token", "refresh_token", "has_session");
      assertThat(findCookie(setCookies, "access_token").getValue()).isEqualTo("access-value");
      assertThat(findCookie(setCookies, "refresh_token").getValue()).isEqualTo("refresh-value");
      assertThat(findCookie(setCookies, "has_session").getValue()).isEqualTo("1");
      assertThat(response.getRedirectedUrl()).isEqualTo(REDIRECT_URI);
    }
  }

  @Nested
  @DisplayName("FailureHandler")
  class FailureHandler {

    @Test
    @DisplayName("예외 메시지 있음 - URLEncoded error 파라미터 포함 리다이렉트")
    void 메시지_있음() throws Exception {
      OAuth2LoginFailureHandler handler =
          new OAuth2LoginFailureHandler(oAuth2Properties, authorizationRequestRepository);
      AuthenticationException exception =
          new OAuth2AuthenticationException(new OAuth2Error("member_banned", "차단된 계정입니다", null));

      MockHttpServletResponse response = new MockHttpServletResponse();
      handler.onAuthenticationFailure(new MockHttpServletRequest(), response, exception);

      String encoded = URLEncoder.encode("차단된 계정입니다", StandardCharsets.UTF_8);
      assertThat(response.getRedirectedUrl()).isEqualTo(REDIRECT_URI + "?error=" + encoded);
    }

    @Test
    @DisplayName("예외 메시지 null - 기본 메시지로 리다이렉트")
    void 메시지_null() throws Exception {
      OAuth2LoginFailureHandler handler =
          new OAuth2LoginFailureHandler(oAuth2Properties, authorizationRequestRepository);
      AuthenticationException exception = new NullMessageAuthenticationException();

      MockHttpServletResponse response = new MockHttpServletResponse();
      handler.onAuthenticationFailure(new MockHttpServletRequest(), response, exception);

      String encoded = URLEncoder.encode("로그인에 실패했습니다", StandardCharsets.UTF_8);
      assertThat(response.getRedirectedUrl()).isEqualTo(REDIRECT_URI + "?error=" + encoded);
    }

    @Test
    @DisplayName("실패 - oauth2 인증 요청 삭제 쿠키 발급")
    void 인증요청_삭제쿠키() throws Exception {
      OAuth2LoginFailureHandler handler =
          new OAuth2LoginFailureHandler(oAuth2Properties, authorizationRequestRepository);
      AuthenticationException exception =
          new OAuth2AuthenticationException(new OAuth2Error("invalid_request", "bad", null));

      MockHttpServletResponse response = new MockHttpServletResponse();
      handler.onAuthenticationFailure(new MockHttpServletRequest(), response, exception);

      HttpCookie authReq = findCookie(allSetCookies(response), "oauth2_auth_request");
      assertThat(authReq).isNotNull();
      assertThat(authReq.getMaxAge()).isZero();
    }
  }
}
