package com.gakkaweo.backend.common.exception;

import java.time.Instant;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@SuppressWarnings("unused")
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

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

  @Override
  protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
      MaxUploadSizeExceededException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    log.warn("파일 크기 초과: {}", ex.getMessage());
    ErrorCode errorCode = ErrorCode.FILE_TOO_LARGE;
    ErrorBody body =
        new ErrorBody(
            errorCode.getStatus().value(),
            errorCode.name(),
            errorCode.getMessage(),
            Instant.now().toString());
    return ResponseEntity.status(errorCode.getStatus()).body(body);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> handleUnexpectedException(Exception e) {
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

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
    log.warn("유효성 검증 실패: {}", message);
    ErrorBody body =
        new ErrorBody(status.value(), "VALIDATION_FAILED", message, Instant.now().toString());
    return ResponseEntity.status(status).body(body);
  }

  @Override
  protected ResponseEntity<Object> handleMissingServletRequestParameter(
      MissingServletRequestParameterException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    log.warn("필수 파라미터 누락: {}", ex.getMessage());
    ErrorBody body =
        new ErrorBody(
            status.value(), "MISSING_PARAMETER", ex.getMessage(), Instant.now().toString());
    return ResponseEntity.status(status).body(body);
  }

  @Override
  protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
      HttpRequestMethodNotSupportedException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    log.warn("지원하지 않는 HTTP 메서드: {}", ex.getMessage());
    ErrorBody body =
        new ErrorBody(
            status.value(), "METHOD_NOT_ALLOWED", ex.getMessage(), Instant.now().toString());
    return ResponseEntity.status(status).body(body);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    log.warn("{}: {}", ex.getClass().getSimpleName(), ex.getMessage());
    ErrorBody errorBody =
        new ErrorBody(
            status.value(),
            ex.getClass().getSimpleName(),
            ex.getMessage(),
            Instant.now().toString());
    return ResponseEntity.status(status).headers(headers).body(errorBody);
  }
}
