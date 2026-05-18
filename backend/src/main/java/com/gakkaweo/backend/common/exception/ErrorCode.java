package com.gakkaweo.backend.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다"),
  INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다"),
  EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다"),
  BLACKLISTED_TOKEN(HttpStatus.UNAUTHORIZED, "로그아웃된 토큰입니다"),
  REFRESH_TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "보안을 위해 현재 로그인 세션이 종료되었습니다"),
  INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다"),
  MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다"),
  NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다"),
  NICKNAME_UNCHANGED(HttpStatus.BAD_REQUEST, "현재 닉네임과 동일합니다"),
  NICKNAME_FORBIDDEN(HttpStatus.BAD_REQUEST, "사용할 수 없는 닉네임입니다"),
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 검증에 실패했습니다"),
  OAUTH_PROVIDER_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인 프로바이더입니다"),
  INVALID_GUESS_TEXT(HttpStatus.BAD_REQUEST, "유효하지 않은 추측 입력입니다"),
  AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 서비스를 일시적으로 이용할 수 없습니다"),

  HINT_NOT_AVAILABLE(HttpStatus.FORBIDDEN, "힌트를 사용하려면 유사도 60% 이상이 필요합니다"),
  SENTENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "오늘의 문제를 찾을 수 없습니다"),
  SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "게임 세션을 찾을 수 없습니다"),
  GAME_EXPIRED(HttpStatus.CONFLICT, "만료된 게임입니다"),
  CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "동시 수정 충돌이 발생했습니다"),
  RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요"),
  DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다"),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다"),
  MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "필수 파라미터가 누락되었습니다"),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다"),
  INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다"),
  FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일 크기가 제한을 초과했습니다"),
  FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다"),
  SSE_MAX_CONNECTIONS(HttpStatus.SERVICE_UNAVAILABLE, "SSE 최대 연결 수를 초과했습니다"),

  SENTENCE_DUPLICATE(HttpStatus.CONFLICT, "이미 등록된 문장입니다"),
  SENTENCE_ALREADY_USED(HttpStatus.BAD_REQUEST, "이미 출제된 문장은 삭제할 수 없습니다"),
  SENTENCE_ALREADY_SCHEDULED(HttpStatus.CONFLICT, "해당 날짜에 이미 스케줄된 문장이 있습니다"),
  CSV_PARSE_ERROR(HttpStatus.BAD_REQUEST, "CSV 파일 파싱에 실패했습니다"),
  ANNOUNCEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "공지를 찾을 수 없습니다"),
  MEMBER_BANNED(HttpStatus.FORBIDDEN, "차단된 계정입니다"),
  ADMIN_SELF_ACTION(HttpStatus.BAD_REQUEST, "자기 자신에 대한 작업은 수행할 수 없습니다"),
  ROLE_ALREADY_ASSIGNED(HttpStatus.BAD_REQUEST, "이미 동일한 역할입니다"),
  INSUFFICIENT_ROLE(HttpStatus.FORBIDDEN, "이 작업을 수행할 권한이 부족합니다"),

  WS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "WebSocket 토큰이 만료되었습니다"),
  WS_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "WebSocket 토큰이 폐기되었습니다"),
  WS_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "WebSocket 요청이 너무 많습니다"),

  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다");

  private final HttpStatus status;
  private final String message;
}
