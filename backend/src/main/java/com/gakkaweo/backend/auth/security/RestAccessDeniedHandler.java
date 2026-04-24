package com.gakkaweo.backend.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gakkaweo.backend.common.exception.ErrorBody;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;
  private final Clock clock;

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    ErrorBody body =
        new ErrorBody(
            HttpStatus.FORBIDDEN.value(),
            "ACCESS_DENIED",
            "접근 권한이 없습니다",
            clock.instant().toString());

    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getWriter(), body);
  }
}
