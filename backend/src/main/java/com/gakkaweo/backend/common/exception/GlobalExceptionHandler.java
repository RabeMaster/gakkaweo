package com.gakkaweo.backend.common.exception;

import java.time.LocalDateTime;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@SuppressWarnings("unused")
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<?> handleBusinessException(BusinessException e) {
    ErrorCode errorCode = e.getErrorCode();
    ErrorBody body =
        new ErrorBody(
            errorCode.getStatus().value(),
            errorCode.name(),
            errorCode.getMessage(),
            LocalDateTime.now().toString());
    return ResponseEntity.status(errorCode.getStatus()).body(body);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<?> handleValidationException(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
    ErrorBody body =
        new ErrorBody(
            HttpStatus.BAD_REQUEST.value(),
            "VALIDATION_FAILED",
            message,
            LocalDateTime.now().toString());
    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<?> handleMissingParameter(MissingServletRequestParameterException e) {
    ErrorBody body =
        new ErrorBody(
            HttpStatus.BAD_REQUEST.value(),
            "MISSING_PARAMETER",
            e.getMessage(),
            LocalDateTime.now().toString());
    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<?> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
    ErrorBody body =
        new ErrorBody(
            HttpStatus.METHOD_NOT_ALLOWED.value(),
            "METHOD_NOT_ALLOWED",
            e.getMessage(),
            LocalDateTime.now().toString());
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<?> handleOptimisticLock(
      ObjectOptimisticLockingFailureException ignoredException) {
    ErrorCode errorCode = ErrorCode.CONCURRENT_MODIFICATION;
    ErrorBody body =
        new ErrorBody(
            errorCode.getStatus().value(),
            errorCode.name(),
            errorCode.getMessage(),
            LocalDateTime.now().toString());
    return ResponseEntity.status(errorCode.getStatus()).body(body);
  }

  record ErrorBody(int status, String code, String message, String timestamp) {}
}
