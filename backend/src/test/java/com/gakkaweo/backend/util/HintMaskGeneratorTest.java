package com.gakkaweo.backend.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.game.util.HintMaskGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HintMaskGenerator 단위 테스트")
class HintMaskGeneratorTest {

  private final HintMaskGenerator generator = new HintMaskGenerator();

  @Test
  @DisplayName("단어별 글자 수 + 밑줄 마스크")
  void 마스크_생성() {
    HintMaskGenerator.HintMask mask = generator.generate("오늘 날씨가 좋다");

    assertThat(mask.charCounts()).containsExactly(2, 3, 2);
    assertThat(mask.mask()).isEqualTo("__ ___ __");
  }

  @Test
  @DisplayName("단일 단어")
  void 단일_단어() {
    HintMaskGenerator.HintMask mask = generator.generate("안녕");
    assertThat(mask.charCounts()).containsExactly(2);
    assertThat(mask.mask()).isEqualTo("__");
  }

  @Test
  @DisplayName("연속 공백 - split 정규식으로 빈 토큰 제외")
  void 연속공백() {
    HintMaskGenerator.HintMask mask = generator.generate("가  나");
    assertThat(mask.charCounts()).containsExactly(1, 1);
  }
}
