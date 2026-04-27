package com.gakkaweo.backend.auth.oauth2.handler;

import com.gakkaweo.backend.auth.config.OAuth2Properties;
import com.gakkaweo.backend.auth.oauth2.CookieAuthorizationRequestRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

  private final OAuth2Properties oAuth2Properties;
  private final CookieAuthorizationRequestRepository authorizationRequestRepository;

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    log.warn("OAuth2 로그인 실패: {}", exception.getMessage());
    authorizationRequestRepository.deleteCookie(response);

    String message = exception.getMessage() != null ? exception.getMessage() : "로그인에 실패했습니다";
    String errorMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
    response.sendRedirect(oAuth2Properties.getAuthorizedRedirectUri() + "?error=" + errorMessage);
  }
}
