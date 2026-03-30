package com.gakkaweo.backend.auth.util;

import com.gakkaweo.backend.auth.config.CookieProperties;
import com.gakkaweo.backend.auth.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CookieUtils {

  private final JwtProperties jwtProperties;
  private final CookieProperties cookieProperties;

  public ResponseCookie createAccessTokenCookie(String token) {
    return applyDomain(
            ResponseCookie.from("access_token", token)
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .path("/")
                .maxAge(jwtProperties.getAccessExpiration())
                .sameSite("Lax"))
        .build();
  }

  public ResponseCookie createRefreshTokenCookie(String token) {
    return applyDomain(
            ResponseCookie.from("refresh_token", token)
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .path("/auth/refresh")
                .maxAge(jwtProperties.getRefreshExpiration())
                .sameSite("Lax"))
        .build();
  }

  public ResponseCookie deleteAccessTokenCookie() {
    return applyDomain(
            ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .path("/")
                .maxAge(0)
                .sameSite("Lax"))
        .build();
  }

  public ResponseCookie deleteRefreshTokenCookie() {
    return applyDomain(
            ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .path("/auth/refresh")
                .maxAge(0)
                .sameSite("Lax"))
        .build();
  }

  public ResponseCookie createSessionIndicatorCookie() {
    return applyDomain(
            ResponseCookie.from("has_session", "1")
                .httpOnly(false)
                .secure(cookieProperties.isSecure())
                .path("/")
                .maxAge(jwtProperties.getRefreshExpiration())
                .sameSite("Lax"))
        .build();
  }

  public ResponseCookie deleteSessionIndicatorCookie() {
    return applyDomain(
            ResponseCookie.from("has_session", "")
                .httpOnly(false)
                .secure(cookieProperties.isSecure())
                .path("/")
                .maxAge(0)
                .sameSite("Lax"))
        .build();
  }

  private ResponseCookie.ResponseCookieBuilder applyDomain(
      ResponseCookie.ResponseCookieBuilder builder) {
    String domain = cookieProperties.getDomain();
    if (domain != null && !domain.isBlank()) {
      builder.domain(domain);
    }
    return builder;
  }
}
