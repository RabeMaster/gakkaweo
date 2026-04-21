package com.gakkaweo.backend.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.auth.dto.LoginRequest;
import com.gakkaweo.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

@DisplayName("Rate Limit 통합 테스트")
@TestPropertySource(properties = {"app.rate-limit.auth-per-minute=3"})
class RateLimitIntegrationTest extends IntegrationTestBase {

  @Test
  @DisplayName("auth 엔드포인트 - 3회 이후 429 + Retry-After 헤더")
  void 레이트리밋_초과_429() {
    LoginRequest request = new LoginRequest("nonexistent", "wrongpassword");

    for (int i = 0; i < 3; i++) {
      ResponseEntity<String> response =
          restTemplate.postForEntity(url("/auth/login"), request, String.class);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    ResponseEntity<String> limited =
        restTemplate.postForEntity(url("/auth/login"), request, String.class);
    assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(limited.getHeaders().getFirst("Retry-After")).isNotBlank();
  }
}
