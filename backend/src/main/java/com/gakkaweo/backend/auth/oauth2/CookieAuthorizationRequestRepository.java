package com.gakkaweo.backend.auth.oauth2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gakkaweo.backend.auth.config.CookieProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CookieAuthorizationRequestRepository
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

  private static final String COOKIE_NAME = "oauth2_auth_request";
  private static final int COOKIE_EXPIRE_SECONDS = 300;

  private final CookieProperties cookieProperties;
  private final ObjectMapper objectMapper;

  @Override
  public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
    return findCookieValue(request);
  }

  @Override
  public void saveAuthorizationRequest(
      OAuth2AuthorizationRequest authorizationRequest,
      HttpServletRequest request,
      HttpServletResponse response) {
    if (authorizationRequest == null) {
      deleteCookie(response);
      return;
    }

    String serialized = serialize(authorizationRequest);
    ResponseCookie cookie =
        ResponseCookie.from(COOKIE_NAME, serialized)
            .path("/")
            .httpOnly(true)
            .secure(cookieProperties.secure())
            .sameSite("Lax")
            .maxAge(COOKIE_EXPIRE_SECONDS)
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  @Override
  public OAuth2AuthorizationRequest removeAuthorizationRequest(
      HttpServletRequest request, HttpServletResponse response) {
    OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
    if (authorizationRequest != null) {
      deleteCookie(response);
    }
    return authorizationRequest;
  }

  public void deleteCookie(HttpServletResponse response) {
    ResponseCookie cookie =
        ResponseCookie.from(COOKIE_NAME, "")
            .path("/")
            .httpOnly(true)
            .secure(cookieProperties.secure())
            .sameSite("Lax")
            .maxAge(0)
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  private OAuth2AuthorizationRequest findCookieValue(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (COOKIE_NAME.equals(cookie.getName())) {
        return deserialize(cookie.getValue());
      }
    }
    return null;
  }

  private String serialize(OAuth2AuthorizationRequest request) {
    Map<String, Object> data =
        Map.of(
            "authorizationUri", request.getAuthorizationUri(),
            "clientId", request.getClientId(),
            "redirectUri", request.getRedirectUri(),
            "scopes", request.getScopes(),
            "state", request.getState(),
            "additionalParameters", request.getAdditionalParameters(),
            "authorizationRequestUri", request.getAuthorizationRequestUri(),
            "attributes", request.getAttributes());
    try {
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(objectMapper.writeValueAsBytes(data));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("OAuth2 인증 요청 직렬화 실패", e);
    }
  }

  @SuppressWarnings("unchecked")
  private OAuth2AuthorizationRequest deserialize(String value) {
    try {
      byte[] bytes = Base64.getUrlDecoder().decode(value);
      Map<String, Object> data =
          objectMapper.readValue(bytes, new TypeReference<Map<String, Object>>() {});

      return OAuth2AuthorizationRequest.authorizationCode()
          .authorizationUri((String) data.get("authorizationUri"))
          .clientId((String) data.get("clientId"))
          .redirectUri((String) data.get("redirectUri"))
          .scopes(new HashSet<>((List<String>) data.get("scopes")))
          .state((String) data.get("state"))
          .additionalParameters((Map<String, Object>) data.get("additionalParameters"))
          .authorizationRequestUri((String) data.get("authorizationRequestUri"))
          .attributes(attrs -> attrs.putAll((Map<String, Object>) data.get("attributes")))
          .build();
    } catch (IOException e) {
      throw new IllegalStateException("OAuth2 인증 요청 역직렬화 실패", e);
    }
  }
}
