package com.gakkaweo.backend.auth.websocket;

import com.gakkaweo.backend.auth.jwt.JwtProvider;
import com.gakkaweo.backend.auth.security.CustomUserDetails;
import io.jsonwebtoken.Claims;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

  static final String ATTR_PRINCIPAL = "stompPrincipal";
  static final String ATTR_JTI = "jti";
  static final String ATTR_EXPIRES_AT = "expiresAt";

  private final JwtProvider jwtProvider;

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal()
            instanceof CustomUserDetails(java.util.UUID publicId, String role))) {
      return false;
    }

    String token = extractTokenFromCookieHeader(request);
    if (token == null) {
      return false;
    }

    Claims claims = jwtProvider.parseAccessToken(token);
    String jti = claims.getId();
    long expiresAt = claims.getExpiration().getTime();

    attributes.put(ATTR_PRINCIPAL, new StompPrincipal(publicId, role));
    attributes.put(ATTR_JTI, jti);
    attributes.put(ATTR_EXPIRES_AT, expiresAt);

    return true;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {}

  private String extractTokenFromCookieHeader(ServerHttpRequest request) {
    String cookieHeader = request.getHeaders().getFirst(HttpHeaders.COOKIE);
    if (cookieHeader == null) {
      return null;
    }
    for (String part : cookieHeader.split(";")) {
      String trimmed = part.trim();
      if (trimmed.startsWith("access_token=")) {
        return trimmed.substring("access_token=".length());
      }
    }
    return null;
  }
}
