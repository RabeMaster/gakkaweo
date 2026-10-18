package com.gakkaweo.backend.auth.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gakkaweo.backend.auth.service.AuthSessionService;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorBody;
import com.gakkaweo.backend.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final Set<String> TOKEN_ENDPOINTS =
      Set.of("/auth/login", "/auth/register", "/auth/refresh", "/auth/logout");

  private final JwtProvider jwtProvider;
  private final AuthSessionService authSessionService;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  @Value("${management.server.port:#{null}}")
  private Integer managementPort;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return managementPort != null && request.getLocalPort() == managementPort;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // 기존 로그인 상태와 관계없이 로그인·회원가입·갱신·로그아웃 요청을 처리합니다.
    if ("POST".equals(request.getMethod()) && TOKEN_ENDPOINTS.contains(request.getRequestURI())) {
      filterChain.doFilter(request, response);
      return;
    }

    String token = extractTokenFromCookie(request);

    if (token == null || !jwtProvider.validateAccessToken(token)) {
      filterChain.doFilter(request, response);
      return;
    }

    Claims claims = jwtProvider.parseAccessToken(token);
    String sessionId = claims.get("sid", String.class);

    if (sessionId == null) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      UUID publicId = UUID.fromString(claims.getSubject());
      var userDetails =
          authSessionService.authenticate(
              UUID.fromString(sessionId), publicId, claims.get("role", String.class));

      if (userDetails != null) {
        var authentication =
            new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
      }
    } catch (BusinessException | IllegalArgumentException e) {
      ErrorCode code =
          e instanceof BusinessException businessException
              ? businessException.getErrorCode()
              : ErrorCode.UNAUTHORIZED;
      writeError(response, code);
      return;
    } catch (DataAccessException | TransactionException e) {
      writeError(response, ErrorCode.AUTH_SERVICE_UNAVAILABLE);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private String extractTokenFromCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }

    for (Cookie cookie : cookies) {
      if ("access_token".equals(cookie.getName())) {
        return cookie.getValue();
      }
    }

    return null;
  }

  private void writeError(HttpServletResponse response, ErrorCode code) throws IOException {
    response.setStatus(code.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(
        response.getWriter(),
        new ErrorBody(
            code.getStatus().value(), code.name(), code.getMessage(), clock.instant().toString()));
  }
}
