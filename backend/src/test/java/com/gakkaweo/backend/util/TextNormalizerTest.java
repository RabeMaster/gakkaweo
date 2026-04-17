package com.gakkaweo.backend.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.infra.ai.service.TextNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TextNormalizer 단위 테스트")
class TextNormalizerTest {

  private final TextNormalizer normalizer = new TextNormalizer();

  @Test
  @DisplayName("특수문자 제거")
  void 특수문자_제거() {
    assertThat(normalizer.normalize("안녕! 하세요?")).isEqualTo("안녕 하세요");
    assertThat(normalizer.normalize("hello, world.")).isEqualTo("hello world");
  }

  @Test
  @DisplayName("연속 공백 축약")
  void 공백_축약() {
    assertThat(normalizer.normalize("a   b   c")).isEqualTo("a b c");
  }

  @Test
  @DisplayName("한글/영문/숫자 보존")
  void 보존() {
    assertThat(normalizer.normalize("가나다123abc")).isEqualTo("가나다123abc");
  }

  @Test
  @DisplayName("공백/특수문자만 → 빈 문자열")
  void 전체_특수문자() {
    assertThat(normalizer.normalize("!!!")).isEmpty();
    assertThat(normalizer.normalize("   ")).isEmpty();
  }

  @Test
  @DisplayName("해시 - 동일 입력 동일 해시")
  void 해시_일관성() {
    String a = normalizer.hashForCache("hello");
    String b = normalizer.hashForCache("hello");
    assertThat(a).isEqualTo(b).hasSize(64);
  }

  @Test
  @DisplayName("해시 - 다른 입력 다른 해시")
  void 해시_차이() {
    assertThat(normalizer.hashForCache("a")).isNotEqualTo(normalizer.hashForCache("b"));
  }
}
