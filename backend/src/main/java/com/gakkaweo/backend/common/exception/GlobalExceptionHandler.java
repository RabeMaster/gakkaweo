package com.gakkaweo.backend.common.exception;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
    ErrorCode errorCode = e.getErrorCode();
    Map<String, Object> body =
        Map.of(
            "status", errorCode.getStatus().value(),
            "code", errorCode.name(),
            "message", errorCode.getMessage(),
            "timestamp", LocalDateTime.now().toString());
    return ResponseEntity.status(errorCode.getStatus()).body(body);
  }
}
