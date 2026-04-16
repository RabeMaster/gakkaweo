package com.gakkaweo.backend.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "오늘의 문제 응답")
public record TodayResponse(
    @Schema(description = "오늘 문제 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID sentenceId,
    @Schema(description = "초성 힌트 마스크", example = "ㅎㄱ ㅎㄴㄹ") String hintMask,
    @Schema(description = "단어 수", example = "3") int wordCount,
    @Schema(description = "각 단어의 글자 수", example = "[2, 3, 2]") List<Integer> charCounts,
    @Schema(description = "문제 만료 시각 (자정 KST)", example = "2026-04-17T15:00:00Z") Instant expiresAt,
    @Schema(description = "어제 정답 문장", nullable = true, example = "하늘이 맑다") String yesterdaySentence,
    @Schema(description = "어제 날짜", nullable = true, example = "2026-04-16")
        LocalDate yesterdayDate) {}
