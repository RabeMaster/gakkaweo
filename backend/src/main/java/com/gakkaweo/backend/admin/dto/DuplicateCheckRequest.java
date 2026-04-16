package com.gakkaweo.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "중복 검사 요청")
public record DuplicateCheckRequest(
    @Schema(description = "검사할 문장", example = "오늘 날씨가 좋다") @NotBlank @Size(max = 500)
        String sentence) {}
