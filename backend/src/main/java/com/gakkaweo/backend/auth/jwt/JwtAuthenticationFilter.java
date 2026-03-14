package com.gakkaweo.backend.auth.jwt;

import static com.gakkaweo.backend.auth.service.AuthService.BLACKLIST_PREFIX;

import com.gakkaweo.backend.auth.security.CustomUserDetails;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtProvider jwtProvider;
  private final StringRedisTemplate redisTemplate;

  public JwtAuthenticationFilter(JwtProvider jwtProvider, StringRedisTemplate redisTemplate) {
    this.jwtProvider = jwtProvider;
    this.redisTemplate = redisTemplate;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String token = extractTokenFromCookie(request);

    if (token != null && jwtProvider.validateAccessToken(token)) {
      Claims claims = jwtProvider.parseAccessToken(token);
      String jti = claims.getId();

      if (!isBlacklisted(jti)) {
        UUID publicId = UUID.fromString(claims.getSubject());
        String role = claims.get("role", String.class);

        CustomUserDetails userDetails = new CustomUserDetails(publicId, role);
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
      }
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

  private boolean isBlacklisted(String jti) {
    return redisTemplate.hasKey(BLACKLIST_PREFIX + jti);
  }
}
