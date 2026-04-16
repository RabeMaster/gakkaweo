package com.gakkaweo.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "유사도 테스트 요청")
public record SimilarityTestRequest(
    @Schema(description = "기준 문장", example = "오늘 날씨가 좋다") @NotBlank @Size(max = 500)
        String sentence,
    @Schema(description = "비교 텍스트", example = "날씨가 맑다") @NotBlank @Size(max = 200)
        String guessText) {}
