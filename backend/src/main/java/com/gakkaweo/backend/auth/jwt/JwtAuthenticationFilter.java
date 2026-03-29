package com.gakkaweo.backend.auth.jwt;

import static com.gakkaweo.backend.common.redis.RedisKeyConstants.BLACKLIST_PREFIX;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gakkaweo.backend.auth.security.CustomUserDetails;
import com.gakkaweo.backend.common.exception.ErrorBody;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtProvider jwtProvider;
  private final StringRedisTemplate redisTemplate;
  private final MemberRepository memberRepository;
  private final ObjectMapper objectMapper;

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

        Optional<Member> member = memberRepository.findByPublicId(publicId);
        if (member.isPresent() && member.get().getBanned()) {
          writeBannedResponse(response);
          return;
        }

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

  private void writeBannedResponse(HttpServletResponse response) throws IOException {
    ErrorCode errorCode = ErrorCode.MEMBER_BANNED;
    ErrorBody body =
        new ErrorBody(
            errorCode.getStatus().value(),
            errorCode.name(),
            errorCode.getMessage(),
            Instant.now().toString());
    response.setStatus(errorCode.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getWriter(), body);
  }
}
