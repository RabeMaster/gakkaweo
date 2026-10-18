package com.gakkaweo.backend.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.common.util.TextNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TextNormalizer 단위 테스트")
class TextNormalizerTest {

  private final TextNormalizer normalizer = new TextNormalizer();

  @Test
  @DisplayName("Unicode White_Space 전체 범위와 FE·AI 입력 계약 일치")
  void 전체_공백_범위() {
    int[] spaces = {
      9, 10, 11, 12, 13, 32, 0x85, 0xa0, 0x1680, 0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005,
      0x2006, 0x2007, 0x2008, 0x2009, 0x200a, 0x2028, 0x2029, 0x202f, 0x205f, 0x3000
    };
    for (int code : spaces) {
      String space = Character.toString(code);
      assertThat(normalizer.normalize(space + "안녕" + space + space + "세계" + space))
          .isEqualTo("안녕 세계");
    }
  }

  @Test
  @DisplayName("공백 범위 밖 제어 문자 제거 및 NFC·길이 경계 일치")
  void 공통_입력_사례() {
    for (int code : new int[] {0xfeff, 0x1c, 0x1d, 0x1e, 0x1f}) {
      assertThat(normalizer.normalize("안녕" + Character.toString(code) + "세계!")).isEqualTo("안녕세계");
    }
    assertThat(normalizer.normalize("가나다! ABC?! 123🙂")).isEqualTo("가나다 ABC 123");
    assertThat(normalizer.normalize("🙂🙂")).isEmpty();
    assertThat(normalizer.normalize("ㄱ디")).isEqualTo("디");
    assertThat(normalizer.normalize("가!나")).isEqualTo("가나");
    assertThat(normalizer.normalize("가".repeat(200))).isEqualTo("가".repeat(200));
  }

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
  @DisplayName("Unicode White_Space와 NFC 분해 한글을 동일하게 정규화")
  void 유니코드_공백_NFC() {
    assertThat(normalizer.normalize("\u00a0가나다\u3000\u0085라마\u00a0")).isEqualTo("가나다 라마");
    assertThat(normalizer.normalize("\u1100\u1161\u1102\u1161 다")).isEqualTo("가나 다");
    assertThat(normalizer.normalize("가나\ufeff\u001c다")).isEqualTo("가나다");
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
