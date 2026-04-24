package com.gakkaweo.backend.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.gakkaweo.backend.common.exception.ErrorBody;
import com.gakkaweo.backend.common.exception.GlobalExceptionHandler;
import com.gakkaweo.backend.infra.notification.ServerErrorNotifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@DisplayName("GlobalExceptionHandler 단위 테스트 (나머지 분기)")
class GlobalExceptionHandlerUnitTest {

  private final ServerErrorNotifier serverErrorNotifier = mock(ServerErrorNotifier.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
  private final GlobalExceptionHandler handler =
      new GlobalExceptionHandler(serverErrorNotifier, clock);

  private static ResponseEntity<Object> invokeHandleMaxUploadSizeExceeded(
      GlobalExceptionHandler handler,
      MaxUploadSizeExceededException ex,
      HttpStatus status,
      ServletWebRequest req)
      throws Exception {
    var method =
        GlobalExceptionHandler.class.getDeclaredMethod(
            "handleMaxUploadSizeExceededException",
            MaxUploadSizeExceededException.class,
            HttpHeaders.class,
            org.springframework.http.HttpStatusCode.class,
            org.springframework.web.context.request.WebRequest.class);
    method.setAccessible(true);
    @SuppressWarnings("unchecked")
    ResponseEntity<Object> response =
        (ResponseEntity<Object>) method.invoke(handler, ex, new HttpHeaders(), status, req);
    return response;
  }

  private static ResponseEntity<Object> invokeHandleMissingParameter(
      GlobalExceptionHandler handler,
      MissingServletRequestParameterException ex,
      HttpStatus status,
      ServletWebRequest req)
      throws Exception {
    var method =
        GlobalExceptionHandler.class.getDeclaredMethod(
            "handleMissingServletRequestParameter",
            MissingServletRequestParameterException.class,
            HttpHeaders.class,
            org.springframework.http.HttpStatusCode.class,
            org.springframework.web.context.request.WebRequest.class);
    method.setAccessible(true);
    @SuppressWarnings("unchecked")
    ResponseEntity<Object> response =
        (ResponseEntity<Object>) method.invoke(handler, ex, new HttpHeaders(), status, req);
    return response;
  }

  private static ResponseEntity<Object> invokeHandleMethodNotSupported(
      GlobalExceptionHandler handler,
      HttpRequestMethodNotSupportedException ex,
      HttpStatus status,
      ServletWebRequest req)
      throws Exception {
    var method =
        GlobalExceptionHandler.class.getDeclaredMethod(
            "handleHttpRequestMethodNotSupported",
            HttpRequestMethodNotSupportedException.class,
            HttpHeaders.class,
            org.springframework.http.HttpStatusCode.class,
            org.springframework.web.context.request.WebRequest.class);
    method.setAccessible(true);
    @SuppressWarnings("unchecked")
    ResponseEntity<Object> response =
        (ResponseEntity<Object>) method.invoke(handler, ex, new HttpHeaders(), status, req);
    return response;
  }

  @Test
  @DisplayName("ObjectOptimisticLockingFailureException - CONCURRENT_MODIFICATION 응답")
  void 낙관락_충돌() {
    ObjectOptimisticLockingFailureException ex =
        new ObjectOptimisticLockingFailureException("GameSession", 1L);

    ResponseEntity<?> response = handler.handleOptimisticLock(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    ErrorBody body = (ErrorBody) response.getBody();
    assertThat(body.code()).isEqualTo("CONCURRENT_MODIFICATION");
  }

  @Test
  @DisplayName("handleUnexpectedException - 500 INTERNAL_SERVER_ERROR 응답 + ServerErrorNotifier 위임")
  void 예상밖_예외() {
    Exception ex = new RuntimeException("boom");
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/boom");

    ResponseEntity<?> response = handler.handleUnexpectedException(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    ErrorBody body = (ErrorBody) response.getBody();
    assertThat(body.code()).isEqualTo("INTERNAL_SERVER_ERROR");
    verify(serverErrorNotifier).notify(eq(ex), any());
  }

  @Test
  @DisplayName("MaxUploadSizeExceededException - FILE_TOO_LARGE 응답")
  void 업로드_초과() throws Exception {
    MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(1024L);
    ServletWebRequest req = new ServletWebRequest(new MockHttpServletRequest());

    ResponseEntity<Object> response =
        invokeHandleMaxUploadSizeExceeded(handler, ex, HttpStatus.PAYLOAD_TOO_LARGE, req);

    assertThat(response).isNotNull();
    ErrorBody body = (ErrorBody) response.getBody();
    assertThat(body.code()).isEqualTo("FILE_TOO_LARGE");
  }

  @Test
  @DisplayName("MissingServletRequestParameterException - MISSING_PARAMETER 응답")
  void 필수파라미터_누락() throws Exception {
    MissingServletRequestParameterException ex =
        new MissingServletRequestParameterException("sentenceId", "String");
    ServletWebRequest req = new ServletWebRequest(new MockHttpServletRequest());

    ResponseEntity<Object> response =
        invokeHandleMissingParameter(handler, ex, HttpStatus.BAD_REQUEST, req);

    assertThat(response).isNotNull();
    ErrorBody body = (ErrorBody) response.getBody();
    assertThat(body.code()).isEqualTo("MISSING_PARAMETER");
  }

  @Test
  @DisplayName("HttpRequestMethodNotSupported - METHOD_NOT_ALLOWED 응답")
  void 지원안하는_method() throws Exception {
    HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("PATCH");
    ServletWebRequest req = new ServletWebRequest(new MockHttpServletRequest());

    ResponseEntity<Object> response =
        invokeHandleMethodNotSupported(handler, ex, HttpStatus.METHOD_NOT_ALLOWED, req);

    assertThat(response).isNotNull();
    ErrorBody body = (ErrorBody) response.getBody();
    assertThat(body.code()).isEqualTo("METHOD_NOT_ALLOWED");
  }
}
