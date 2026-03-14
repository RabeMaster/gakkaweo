package com.gakkaweo.backend.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다"),
  EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다"),
  BLACKLISTED_TOKEN(HttpStatus.UNAUTHORIZED, "로그아웃된 토큰입니다"),
  REFRESH_TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "보안을 위해 현재 로그인 세션이 종료되었습니다"),
  INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다"),
  MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다"),
  OAUTH_PROVIDER_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인 프로바이더입니다"),
  AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 서비스를 일시적으로 이용할 수 없습니다");

  private final HttpStatus status;
  private final String message;
}
