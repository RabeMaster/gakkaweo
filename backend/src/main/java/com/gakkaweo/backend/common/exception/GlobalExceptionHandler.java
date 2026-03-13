package com.gakkaweo.backend.common.exception;

import java.time.LocalDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
    ErrorCode errorCode = e.getErrorCode();
    ErrorResponse body =
        new ErrorResponse(
            errorCode.getStatus().value(),
            errorCode.name(),
            errorCode.getMessage(),
            LocalDateTime.now().toString());
    return ResponseEntity.status(errorCode.getStatus()).body(body);
  }

  private record ErrorResponse(int status, String code, String message, String timestamp) {}
}
