package com.gakkaweo.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "문장 등록 요청")
public record SentenceCreateRequest(
    @Schema(description = "등록할 문장 (최대 500자)", example = "오늘 날씨가 좋다") @NotBlank @Size(max = 500)
        String sentence) {}
