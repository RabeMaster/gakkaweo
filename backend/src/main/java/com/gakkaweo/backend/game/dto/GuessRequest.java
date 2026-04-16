package com.gakkaweo.backend.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "추측 요청")
public record GuessRequest(
    @Schema(description = "오늘 문제 ID", example = "550e8400-e29b-41d4-a716-446655440000") @NotNull
        UUID sentenceId,
    @Schema(description = "추측 텍스트 (2~200자)", example = "오늘 날씨가 좋다")
        @NotBlank
        @Size(min = 2, max = 200)
        String guessText) {}
