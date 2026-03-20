package com.gakkaweo.backend.ratelimit.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gakkaweo.backend.auth.security.CustomUserDetails;
import com.gakkaweo.backend.common.exception.ErrorCode;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

  private final EndpointGroupResolver endpointGroupResolver;
  private final BucketStore bucketStore;
  private final ObjectMapper objectMapper;

  public RateLimitFilter(
      EndpointGroupResolver endpointGroupResolver,
      BucketStore bucketStore,
      ObjectMapper objectMapper) {
    this.endpointGroupResolver = endpointGroupResolver;
    this.bucketStore = bucketStore;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    EndpointGroup group =
        endpointGroupResolver.resolve(request.getMethod(), request.getRequestURI());

    if (group == EndpointGroup.NONE) {
      filterChain.doFilter(request, response);
      return;
    }

    String key = resolveKey(group, request);
    Bucket bucket = bucketStore.resolveBucket(group, key);
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

    if (probe.isConsumed()) {
      response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
      filterChain.doFilter(request, response);
      return;
    }

    long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
    log.warn("Rate limit 초과: group={}, key={}, retryAfter={}s", group, key, retryAfterSeconds);

    ErrorCode errorCode = ErrorCode.RATE_LIMIT_EXCEEDED;
    Map<String, Object> body =
        Map.of(
            "status", errorCode.getStatus().value(),
            "code", errorCode.name(),
            "message", errorCode.getMessage(),
            "timestamp", Instant.now().toString());

    response.setStatus(errorCode.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
    objectMapper.writeValue(response.getWriter(), body);
  }

  private String resolveKey(EndpointGroup group, HttpServletRequest request) {
    if (group == EndpointGroup.AUTH) {
      return request.getRemoteAddr();
    }

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails user) {
      return user.publicId().toString();
    }

    return request.getRemoteAddr();
  }
}
