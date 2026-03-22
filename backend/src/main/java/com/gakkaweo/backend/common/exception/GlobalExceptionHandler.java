package com.gakkaweo.backend.common.exception;

import java.time.Instant;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@SuppressWarnings("unused")
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<?> handleBusinessException(BusinessException e) {
    ErrorCode errorCode = e.getErrorCode();
    log.warn("비즈니스 예외 발생: {} - {}", errorCode.name(), errorCode.getMessage());
    ErrorBody body =
        new ErrorBody(
            errorCode.getStatus().value(),
            errorCode.name(),
            errorCode.getMessage(),
            Instant.now().toString());
    return ResponseEntity.status(errorCode.getStatus()).body(body);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<?> handleValidationException(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
    log.warn("유효성 검증 실패: {}", message);
    ErrorBody body =
        new ErrorBody(
            HttpStatus.BAD_REQUEST.value(), "VALIDATION_FAILED", message, Instant.now().toString());
    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<?> handleMissingParameter(MissingServletRequestParameterException e) {
    log.warn("필수 파라미터 누락: {}", e.getMessage());
    ErrorBody body =
        new ErrorBody(
            HttpStatus.BAD_REQUEST.value(),
            "MISSING_PARAMETER",
            e.getMessage(),
            Instant.now().toString());
    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<?> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
    log.warn("지원하지 않는 HTTP 메서드: {}", e.getMessage());
    ErrorBody body =
        new ErrorBody(
            HttpStatus.METHOD_NOT_ALLOWED.value(),
            "METHOD_NOT_ALLOWED",
            e.getMessage(),
            Instant.now().toString());
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<?> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
    log.warn("낙관적 락 충돌 발생: entity={}, id={}", e.getPersistentClassName(), e.getIdentifier());
    ErrorCode errorCode = ErrorCode.CONCURRENT_MODIFICATION;
    ErrorBody body =
        new ErrorBody(
            errorCode.getStatus().value(),
            errorCode.name(),
            errorCode.getMessage(),
            Instant.now().toString());
    return ResponseEntity.status(errorCode.getStatus()).body(body);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> handleException(Exception e) {
    log.error("예상하지 못한 서버 오류 발생", e);
    ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
    ErrorBody body =
        new ErrorBody(
            errorCode.getStatus().value(),
            errorCode.name(),
            errorCode.getMessage(),
            Instant.now().toString());
    return ResponseEntity.status(errorCode.getStatus()).body(body);
  }
}
