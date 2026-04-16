package com.gakkaweo.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "중복 검사 결과")
public record DuplicateCheckResponse(
    @Schema(description = "중복 여부", example = "true") boolean hasDuplicate,
    @Schema(description = "유사 문장 목록") List<SimilarEntry> similarEntries) {

  @Schema(description = "유사 문장")
  public record SimilarEntry(
      @Schema(description = "문장", example = "오늘 날씨가 좋다") String sentence,
      @Schema(description = "유사도", example = "97.85") BigDecimal similarity) {}
}
