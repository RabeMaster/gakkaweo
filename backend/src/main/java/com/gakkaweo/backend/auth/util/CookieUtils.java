package com.gakkaweo.backend.auth.util;

import com.gakkaweo.backend.auth.config.CookieProperties;
import com.gakkaweo.backend.auth.config.JwtProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieUtils {

  private final JwtProperties jwtProperties;
  private final CookieProperties cookieProperties;

  public CookieUtils(JwtProperties jwtProperties, CookieProperties cookieProperties) {
    this.jwtProperties = jwtProperties;
    this.cookieProperties = cookieProperties;
  }

  public ResponseCookie createAccessTokenCookie(String token) {
    return ResponseCookie.from("access_token", token)
        .httpOnly(true)
        .secure(cookieProperties.isSecure())
        .path("/")
        .maxAge(jwtProperties.getAccessExpiration())
        .sameSite("Lax")
        .build();
  }

  public ResponseCookie createRefreshTokenCookie(String token) {
    return ResponseCookie.from("refresh_token", token)
        .httpOnly(true)
        .secure(cookieProperties.isSecure())
        .path("/auth/refresh")
        .maxAge(jwtProperties.getRefreshExpiration())
        .sameSite("Lax")
        .build();
  }

  public ResponseCookie deleteAccessTokenCookie() {
    return ResponseCookie.from("access_token", "")
        .httpOnly(true)
        .secure(cookieProperties.isSecure())
        .path("/")
        .maxAge(0)
        .sameSite("Lax")
        .build();
  }

  public ResponseCookie deleteRefreshTokenCookie() {
    return ResponseCookie.from("refresh_token", "")
        .httpOnly(true)
        .secure(cookieProperties.isSecure())
        .path("/auth/refresh")
        .maxAge(0)
        .sameSite("Lax")
        .build();
  }
}
