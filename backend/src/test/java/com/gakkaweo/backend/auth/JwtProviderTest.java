package com.gakkaweo.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.auth.config.JwtProperties;
import com.gakkaweo.backend.auth.jwt.JwtProvider;
import com.gakkaweo.backend.support.TestClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JWT 발급 및 검증 시간 단위 테스트")
class JwtProviderTest {

  @Test
  @DisplayName("발급과 검증은 같은 주입 Clock을 사용하며 만료 경과 후 거부")
  void 주입_시간_기준_만료() {
    TestClock clock = new TestClock(Instant.parse("2025-01-01T00:00:00Z"));
    String secret = Base64.getEncoder().encodeToString(new byte[32]);
    JwtProvider provider =
        new JwtProvider(
            new JwtProperties(secret, Duration.ofMinutes(30), Duration.ofDays(7)), clock);
    String token = provider.createAccessToken(UUID.randomUUID(), "USER");

    assertThat(provider.validateAccessToken(token)).isTrue();
    clock.advanceBy(Duration.ofMinutes(31));

    assertThat(provider.validateAccessToken(token)).isFalse();
  }
}
