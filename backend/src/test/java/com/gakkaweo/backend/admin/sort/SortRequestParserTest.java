package com.gakkaweo.backend.admin.sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gakkaweo.backend.admin.sort.SortRequestParser.SortSpec;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class SortRequestParserTest {

  private static SortSpec expected(String entityField, Sort.Direction direction) {
    return new SortSpec(entityField, direction);
  }

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
    @DisplayName("null 입력 - 기본 필드/방향")
    void null_입력_기본값_반환() {
      SortSpec spec =
          SortRequestParser.parse(
              null, TestSortField.class, TestSortField.CREATED_AT, Sort.Direction.DESC);

      assertThat(spec).isEqualTo(expected("createdAt", Sort.Direction.DESC));
    }

    @Test
    @DisplayName("빈 문자열 - 기본 필드/방향")
    void 빈문자열_기본값_반환() {
      SortSpec spec =
          SortRequestParser.parse(
              "   ", TestSortField.class, TestSortField.CREATED_AT, Sort.Direction.DESC);

      assertThat(spec).isEqualTo(expected("createdAt", Sort.Direction.DESC));
    }
  }

  @Nested
  @DisplayName("정상 입력")
  class Valid {

    @Test
    @DisplayName("필드 + asc")
    void 필드_asc() {
      SortSpec spec =
          SortRequestParser.parse(
              "nickname,asc", TestSortField.class, TestSortField.CREATED_AT, Sort.Direction.DESC);

      assertThat(spec).isEqualTo(expected("nickname", Sort.Direction.ASC));
    }

    @Test
    @DisplayName("필드 + desc")
    void 필드_desc() {
      SortSpec spec =
          SortRequestParser.parse(
              "nickname,desc", TestSortField.class, TestSortField.CREATED_AT, Sort.Direction.DESC);

      assertThat(spec).isEqualTo(expected("nickname", Sort.Direction.DESC));
    }

    @Test
    @DisplayName("필드만 - 기본 방향 적용")
    void 필드만_기본방향_적용() {
      SortSpec spec =
          SortRequestParser.parse(
              "nickname", TestSortField.class, TestSortField.CREATED_AT, Sort.Direction.ASC);

      assertThat(spec).isEqualTo(expected("nickname", Sort.Direction.ASC));
    }

    @Test
    @DisplayName("대소문자 무관 매핑 (필드)")
    void 필드키_대소문자_무관() {
      SortSpec spec =
          SortRequestParser.parse(
              "NickName,asc", TestSortField.class, TestSortField.CREATED_AT, Sort.Direction.DESC);

      assertThat(spec).isEqualTo(expected("nickname", Sort.Direction.ASC));
    }

    @Test
    @DisplayName("대소문자 무관 매핑 (방향)")
    void 방향_대소문자_무관() {
      SortSpec spec =
          SortRequestParser.parse(
              "nickname,DESC", TestSortField.class, TestSortField.CREATED_AT, Sort.Direction.ASC);

      assertThat(spec).isEqualTo(expected("nickname", Sort.Direction.DESC));
    }

    @Test
    @DisplayName("앞뒤 공백 trim")
    void 공백_trim() {
      SortSpec spec =
          SortRequestParser.parse(
              "  nickname , asc ",
              TestSortField.class,
              TestSortField.CREATED_AT,
              Sort.Direction.DESC);

      assertThat(spec).isEqualTo(expected("nickname", Sort.Direction.ASC));
    }

    @Test
    @DisplayName("fieldKey와 entityField가 다른 경우 entityField로 매핑")
    void fieldKey_entityField_매핑() {
      SortSpec spec =
          SortRequestParser.parse(
              "memberNickname,asc",
              TestSortField.class,
              TestSortField.CREATED_AT,
              Sort.Direction.DESC);

      assertThat(spec).isEqualTo(expected("member.nickname", Sort.Direction.ASC));
    }
  }

  @Nested
  @DisplayName("잘못된 입력")
  class Invalid {

    @Test
    @DisplayName("화이트리스트 외 필드 - VALIDATION_FAILED")
    void 미허용_필드_예외() {
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
    @DisplayName("잘못된 방향 - VALIDATION_FAILED")
    void 잘못된_방향_예외() {
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
    @DisplayName("필드 누락 (콤마로 시작) - VALIDATION_FAILED")
    void 필드_누락_예외() {
      assertThatThrownBy(
              () ->
                  SortRequestParser.parse(
                      ",desc", TestSortField.class, TestSortField.CREATED_AT, Sort.Direction.DESC))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("3개 이상 토큰 - VALIDATION_FAILED")
    void 토큰_초과_예외() {
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
    @DisplayName("빈 방향 토큰 (`nickname,`) - VALIDATION_FAILED")
    void 빈_방향_예외() {
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
