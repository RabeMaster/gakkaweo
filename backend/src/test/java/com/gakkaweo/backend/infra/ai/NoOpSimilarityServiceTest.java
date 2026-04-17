package com.gakkaweo.backend.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.infra.ai.service.NoOpSimilarityService;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("NoOpSimilarityService 단위 테스트")
class NoOpSimilarityServiceTest {

  private final NoOpSimilarityService service = new NoOpSimilarityService();

  @Test
  @DisplayName("testSimilarity - 항상 0")
  void test_zero() {
    assertThat(service.testSimilarity("anything", "anything"))
        .isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  @DisplayName("calculateSimilarity - 항상 0")
  void calc_zero() {
    assertThat(service.calculateSimilarity(1L, "guess", "sentence", Duration.ofMinutes(1)))
        .isEqualByComparingTo(BigDecimal.ZERO);
  }
}
