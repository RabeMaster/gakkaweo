package com.gakkaweo.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.auth.config.CookieProperties;
import com.gakkaweo.backend.auth.config.JwtProperties;
import com.gakkaweo.backend.auth.util.CookieUtils;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

@DisplayName("CookieUtils 단위 테스트")
class CookieUtilsTest {

  private static final JwtProperties jwtProperties =
      new JwtProperties("secret", Duration.ofMinutes(30), Duration.ofDays(7));

  @Test
  @DisplayName("createAccessTokenCookie - HttpOnly, Path=/, Secure=false")
  void access_기본속성() {
    CookieUtils utils = new CookieUtils(jwtProperties, new CookieProperties(false, ""));
    ResponseCookie cookie = utils.createAccessTokenCookie("token-value");

    assertThat(cookie.getName()).isEqualTo("access_token");
    assertThat(cookie.getValue()).isEqualTo("token-value");
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.getPath()).isEqualTo("/");
    assertThat(cookie.isSecure()).isFalse();
    assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMinutes(30));
  }

  @Test
  @DisplayName("createRefreshTokenCookie - Path=/auth/refresh")
  void refreshToken_쿠키_경로() {
    CookieUtils utils = new CookieUtils(jwtProperties, new CookieProperties(false, ""));
    ResponseCookie cookie = utils.createRefreshTokenCookie("rt-value");

    assertThat(cookie.getPath()).isEqualTo("/auth/refresh");
    assertThat(cookie.isHttpOnly()).isTrue();
  }

  @Test
  @DisplayName("createSessionIndicatorCookie - HttpOnly=false, Path=/")
  void 세션_인디케이터_쿠키() {
    CookieUtils utils = new CookieUtils(jwtProperties, new CookieProperties(false, ""));
    ResponseCookie cookie = utils.createSessionIndicatorCookie();

    assertThat(cookie.getName()).isEqualTo("has_session");
    assertThat(cookie.isHttpOnly()).isFalse();
    assertThat(cookie.getPath()).isEqualTo("/");
  }

  @Test
  @DisplayName("deleteXxxCookie - MaxAge=0")
  void 삭제_쿠키_MaxAge_0() {
    CookieUtils utils = new CookieUtils(jwtProperties, new CookieProperties(false, ""));

    assertThat(utils.deleteAccessTokenCookie().getMaxAge().getSeconds()).isEqualTo(0);
    assertThat(utils.deleteRefreshTokenCookie().getMaxAge().getSeconds()).isEqualTo(0);
    assertThat(utils.deleteSessionIndicatorCookie().getMaxAge().getSeconds()).isEqualTo(0);
  }

  @Test
  @DisplayName("domain 설정 - 도메인 값이 쿠키에 반영")
  void domain_적용() {
    CookieUtils utils = new CookieUtils(jwtProperties, new CookieProperties(true, "example.com"));
    ResponseCookie cookie = utils.createAccessTokenCookie("t");

    assertThat(cookie.getDomain()).isEqualTo("example.com");
    assertThat(cookie.isSecure()).isTrue();
  }

  @Test
  @DisplayName("domain 빈 문자열 - 쿠키에 domain 미설정")
  void domain_빈_문자열() {
    CookieUtils utils = new CookieUtils(jwtProperties, new CookieProperties(false, ""));
    ResponseCookie cookie = utils.createAccessTokenCookie("t");

    assertThat(cookie.getDomain()).isNull();
  }
}
