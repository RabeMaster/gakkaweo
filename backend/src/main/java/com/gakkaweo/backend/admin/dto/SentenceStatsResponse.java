package com.gakkaweo.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "문장 통계")
public record SentenceStatsResponse(
    @Schema(description = "총 세션 수", example = "100") long totalSessions,
    @Schema(description = "클리어 세션 수", example = "75") long clearedSessions,
    @Schema(description = "클리어율", example = "75.0") double clearRate,
    @Schema(description = "평균 유사도", example = "82.35") BigDecimal avgSimilarity,
    @Schema(description = "평균 시도 횟수", example = "18.5") double avgAttemptCount) {}
