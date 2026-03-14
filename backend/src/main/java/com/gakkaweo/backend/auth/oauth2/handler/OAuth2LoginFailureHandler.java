package com.gakkaweo.backend.auth.oauth2.handler;

import com.gakkaweo.backend.auth.config.OAuth2Properties;
import com.gakkaweo.backend.auth.oauth2.CookieAuthorizationRequestRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

  private final OAuth2Properties oAuth2Properties;
  private final CookieAuthorizationRequestRepository authorizationRequestRepository;

  public OAuth2LoginFailureHandler(
      OAuth2Properties oAuth2Properties,
      CookieAuthorizationRequestRepository authorizationRequestRepository) {
    this.oAuth2Properties = oAuth2Properties;
    this.authorizationRequestRepository = authorizationRequestRepository;
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    authorizationRequestRepository.deleteCookie(response);

    String errorMessage =
        URLEncoder.encode(exception.getLocalizedMessage(), StandardCharsets.UTF_8);
    response.sendRedirect(oAuth2Properties.getAuthorizedRedirectUri() + "?error=" + errorMessage);
  }
}
