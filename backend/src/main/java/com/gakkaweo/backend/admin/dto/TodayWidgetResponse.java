package com.gakkaweo.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "오늘 현황 위젯")
public record TodayWidgetResponse(
    @Schema(description = "문제 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID sentenceId,
    @Schema(description = "오늘 문장", example = "오늘 날씨가 좋다") String sentence,
    @Schema(description = "참여자 수", example = "42") long totalParticipants,
    @Schema(description = "클리어 수", example = "30") long clearedCount,
    @Schema(description = "진행 중 수", example = "12") long inProgressCount,
    @Schema(description = "평균 유사도", example = "78.50") BigDecimal avgSimilarity,
    @Schema(description = "평균 시도 횟수", example = "15.3") double avgAttemptCount,
    @Schema(description = "미사용 문장 수", example = "85") long unusedSentenceCount,
    @Schema(description = "SSE 연결 수", example = "15") int sseConnectionCount) {}
