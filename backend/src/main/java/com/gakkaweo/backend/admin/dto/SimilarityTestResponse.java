package com.gakkaweo.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "유사도 테스트 결과")
public record SimilarityTestResponse(
    @Schema(description = "기준 문장", example = "오늘 날씨가 좋다") String sentence,
    @Schema(description = "비교 텍스트", example = "날씨가 맑다") String guessText,
    @Schema(description = "유사도", example = "85.72") BigDecimal similarity) {}
