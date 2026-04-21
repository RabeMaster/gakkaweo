package com.gakkaweo.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.DailySentenceStatus;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.game.repository.GuessHistoryRepository;
import com.gakkaweo.backend.domain.game.repository.HintProjection;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("Native Query 통합 테스트")
class NativeQueryTest extends IntegrationTestBase {

  @Autowired DailySentenceRepository dailySentenceRepository;
  @Autowired GuessHistoryRepository guessHistoryRepository;
  @Autowired TransactionTemplate transactionTemplate;

  @Test
  @DisplayName("findRandomUnusedSentence - ACTIVE + usedAt null인 문장만 반환")
  void random_미사용문장() {
    transactionTemplate.executeWithoutResult(
        status -> {
          DailySentence active = new DailySentence("활성 문장");
          dailySentenceRepository.save(active);
          DailySentence used = new DailySentence("사용된 문장");
          used.setStatus(DailySentenceStatus.USED);
          dailySentenceRepository.save(used);
        });

    DailySentence picked =
        dailySentenceRepository
            .findRandomUnusedSentence()
            .orElseThrow(() -> new AssertionError("ACTIVE 문장이 있어야 함"));

    assertThat(picked.getStatus()).isEqualTo(DailySentenceStatus.ACTIVE);
    assertThat(picked.getUsedAt()).isNull();
  }

  @Test
  @DisplayName("findHints - 빈 결과 (데이터 없음)")
  void hints_빈결과() {
    List<HintProjection> hints =
        guessHistoryRepository.findHints(1L, 1L, new BigDecimal("100.0"), 5);
    assertThat(hints).isEmpty();
  }
}
