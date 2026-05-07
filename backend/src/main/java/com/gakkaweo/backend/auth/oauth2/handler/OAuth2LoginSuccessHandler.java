package com.gakkaweo.backend.auth.oauth2.handler;

import com.gakkaweo.backend.auth.config.OAuth2Properties;
import com.gakkaweo.backend.auth.dto.TokenPair;
import com.gakkaweo.backend.auth.oauth2.CookieAuthorizationRequestRepository;
import com.gakkaweo.backend.auth.oauth2.CustomOAuth2User;
import com.gakkaweo.backend.auth.service.AuthService;
import com.gakkaweo.backend.auth.util.CookieUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

  private final AuthService authService;
  private final CookieUtils cookieUtils;
  private final OAuth2Properties oAuth2Properties;
  private final CookieAuthorizationRequestRepository authorizationRequestRepository;

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
    log.info("OAuth2 로그인 성공: memberId={}", oAuth2User.getMember().getPublicId());
    TokenPair tokenPair = authService.issueTokens(oAuth2User.getMember());

    response.addHeader(
        HttpHeaders.SET_COOKIE,
        cookieUtils.createAccessTokenCookie(tokenPair.accessToken()).toString());
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        cookieUtils.createRefreshTokenCookie(tokenPair.refreshToken()).toString());
    response.addHeader(
        HttpHeaders.SET_COOKIE, cookieUtils.createSessionIndicatorCookie().toString());

    authorizationRequestRepository.deleteCookie(response);

    response.sendRedirect(oAuth2Properties.authorizedRedirectUri());
  }
}
