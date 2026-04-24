package com.gakkaweo.backend.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gakkaweo.backend.common.exception.ErrorBody;
import com.gakkaweo.backend.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;
  private final Clock clock;

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
    ErrorBody body =
        new ErrorBody(
            errorCode.getStatus().value(),
            errorCode.name(),
            errorCode.getMessage(),
            clock.instant().toString());

    response.setStatus(errorCode.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getWriter(), body);
  }
}
