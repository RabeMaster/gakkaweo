package com.gakkaweo.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "문장 수정 요청")
public record SentenceUpdateRequest(
    @Schema(description = "수정할 문장 (최대 500자)", example = "수정된 문장입니다") @NotBlank @Size(max = 500)
        String sentence) {}
