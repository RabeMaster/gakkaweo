package com.gakkaweo.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.common.exception.ErrorBody;
import com.gakkaweo.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@DisplayName("GlobalExceptionHandler 통합 테스트")
class GlobalExceptionHandlerTest extends IntegrationTestBase {

  @Test
  @DisplayName("MethodArgumentNotValidException - 400 VALIDATION_FAILED + 필드 메시지")
  void 유효성검증_400() {
    String invalidJson = "{\"username\":\"\",\"password\":\"\"}";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/auth/register"),
            HttpMethod.POST,
            new HttpEntity<>(invalidJson, headers),
            ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
    assertThat(response.getBody().message()).contains("username");
  }

  @Test
  @DisplayName("HttpMessageNotReadable - 잘못된 JSON 400 VALIDATION_FAILED")
  void 잘못된_JSON_400() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/auth/register"),
            HttpMethod.POST,
            new HttpEntity<>("{invalid-json", headers),
            ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
  }

  @Test
  @DisplayName("METHOD_NOT_ALLOWED - 405")
  void 허용되지않은_메서드_405() {
    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/auth/login"),
            HttpMethod.PUT,
            new HttpEntity<>(new HttpHeaders()),
            ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(response.getBody().code()).isEqualTo("METHOD_NOT_ALLOWED");
  }

  @Test
  @DisplayName("BusinessException 변환 - code/status/message 구조")
  void business_예외_형식() {
    ResponseEntity<ErrorBody> response =
        restTemplate.getForEntity(url("/daily/today"), ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().code()).isEqualTo("SENTENCE_NOT_FOUND");
    assertThat(response.getBody().status()).isEqualTo(404);
    assertThat(response.getBody().message()).isEqualTo("오늘의 문제를 찾을 수 없습니다");
    assertThat(response.getBody().timestamp()).isNotBlank();
  }
}
