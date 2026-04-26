package com.gakkaweo.backend.admin.sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class SortRequestParserTest {

  enum TestSortField implements SortField {
    CREATED_AT("createdAt", "createdAt"),
    NICKNAME("nickname", "nickname"),
    NESTED_ALIAS("memberNickname", "member.nickname");

    private final String fieldKey;
    private final String entityField;

    TestSortField(String fieldKey, String entityField) {
      this.fieldKey = fieldKey;
      this.entityField = entityField;
    }

    @Override
    public String fieldKey() {
      return fieldKey;
    }

    @Override
    public String entityField() {
      return entityField;
    }
  }

  @Nested
  @DisplayName("기본값 처리")
  class Defaults {

    @Test
    @DisplayName("null 입력 → 기본 필드/방향")
    void nullRaw_returnsDefault() {
      Sort sort =
          SortRequestParser.parse(
              null, TestSortField.class, TestSortField.CREATED_AT, Sort.Direction.DESC);

      assertThat(sort).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    @DisplayName("빈 문자열 → 기본 필드/방향")
    void blankRaw_returnsDefault() {
      Sort sort =
          SortRequestParser.parse(
              "   ", TestSortField.class, TestSortField.CREATED_AT, Sort.Direction.DESC);

      assertThat(sort).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }
  }

  @Nested
  @DisplayName("정상 입력")
  class Valid {

    @Test
    @DisplayName("필드 + asc")
    void fieldAndAsc() {
      Sort sort =
          SortRequestParser.parse(
              "nickname,asc", TestSortField.class, TestSortField.CREATED_AT, Sort.Direction.DESC);

      assertThat(sort).isEqualTo(Sort.by(Sort.Direction.ASC, "nickname"));
    }

    @Test
    @DisplayName("필드 + desc")
    void fieldAndDesc() {
      Sort sort =
          SortRequestParser.parse(
              "nickname,desc", TestSortField.class, TestSortField.CREATED_AT, Sort.Direction.DESC);

      assertThat(sort).isEqualTo(Sort.by(Sort.Direction.DESC, "nickname"));
    }

    @Test
    @DisplayName("필드만 → 기본 방향 적용")
    void fieldOnly_usesDefaultDirection() {
      Sort sort =
          SortRequestParser.parse(
              "nickname", TestSortField.class, TestSortField.CREATED_AT, Sort.Direction.ASC);

      assertThat(sort).isEqualTo(Sort.by(Sort.Direction.ASC, "nickname"));
    }

    @Test
    @DisplayName("대소문자 무관 매핑 (필드)")
    void fieldKeyIsCaseInsensitive() {
      Sort sort =
          SortRequestParser.parse(
              "NickName,asc", TestSortField.class, TestSortField.CREATED_AT, Sort.Direction.DESC);

      assertThat(sort).isEqualTo(Sort.by(Sort.Direction.ASC, "nickname"));
    }

    @Test
    @DisplayName("대소문자 무관 매핑 (방향)")
    void directionIsCaseInsensitive() {
      Sort sort =
          SortRequestParser.parse(
              "nickname,DESC", TestSortField.class, TestSortField.CREATED_AT, Sort.Direction.ASC);

      assertThat(sort).isEqualTo(Sort.by(Sort.Direction.DESC, "nickname"));
    }

    @Test
    @DisplayName("앞뒤 공백 trim")
    void trimsWhitespace() {
      Sort sort =
          SortRequestParser.parse(
              "  nickname , asc ",
              TestSortField.class,
              TestSortField.CREATED_AT,
              Sort.Direction.DESC);

      assertThat(sort).isEqualTo(Sort.by(Sort.Direction.ASC, "nickname"));
    }

    @Test
    @DisplayName("fieldKey와 entityField가 다른 경우 entityField로 매핑")
    void mapsFieldKeyToEntityField() {
      Sort sort =
          SortRequestParser.parse(
              "memberNickname,asc",
              TestSortField.class,
              TestSortField.CREATED_AT,
              Sort.Direction.DESC);

      assertThat(sort).isEqualTo(Sort.by(Sort.Direction.ASC, "member.nickname"));
    }
  }

  @Nested
  @DisplayName("잘못된 입력")
  class Invalid {

    @Test
    @DisplayName("화이트리스트 외 필드 → VALIDATION_FAILED")
    void unknownField_throws() {
      assertThatThrownBy(
              () ->
                  SortRequestParser.parse(
                      "password,desc",
                      TestSortField.class,
                      TestSortField.CREATED_AT,
                      Sort.Direction.DESC))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("잘못된 방향 → VALIDATION_FAILED")
    void invalidDirection_throws() {
      assertThatThrownBy(
              () ->
                  SortRequestParser.parse(
                      "nickname,sideways",
                      TestSortField.class,
                      TestSortField.CREATED_AT,
                      Sort.Direction.DESC))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("필드 누락 (콤마로 시작) → VALIDATION_FAILED")
    void emptyField_throws() {
      assertThatThrownBy(
              () ->
                  SortRequestParser.parse(
                      ",desc", TestSortField.class, TestSortField.CREATED_AT, Sort.Direction.DESC))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("3개 이상 토큰 → VALIDATION_FAILED")
    void tooManyTokens_throws() {
      assertThatThrownBy(
              () ->
                  SortRequestParser.parse(
                      "nickname,asc,extra",
                      TestSortField.class,
                      TestSortField.CREATED_AT,
                      Sort.Direction.DESC))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("빈 방향 토큰 (`nickname,`) → VALIDATION_FAILED")
    void blankDirection_throws() {
      assertThatThrownBy(
              () ->
                  SortRequestParser.parse(
                      "nickname,",
                      TestSortField.class,
                      TestSortField.CREATED_AT,
                      Sort.Direction.DESC))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }
  }
}
