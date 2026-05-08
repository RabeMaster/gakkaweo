package com.gakkaweo.backend.auth.oauth2.handler;

import com.gakkaweo.backend.auth.config.OAuth2Properties;
import com.gakkaweo.backend.auth.metrics.AuthMetrics;
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
  private final AuthMetrics authMetrics;

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    String provider = extractProvider(request.getRequestURI());
    authMetrics.recordLogin(provider, false);
    log.warn("OAuth2 로그인 실패: {}", exception.getMessage());
    authorizationRequestRepository.deleteCookie(response);

    String message = exception.getMessage() != null ? exception.getMessage() : "로그인에 실패했습니다";
    String errorMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
    response.sendRedirect(oAuth2Properties.authorizedRedirectUri() + "?error=" + errorMessage);
  }

  private String extractProvider(String uri) {
    String prefix = "/login/oauth2/code/";
    int idx = uri.indexOf(prefix);
    if (idx < 0) {
      return "unknown";
    }
    String segment = uri.substring(idx + prefix.length());
    int slash = segment.indexOf('/');
    return slash > 0 ? segment.substring(0, slash) : segment;
  }
}
