package com.gakkaweo.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "시스템 상태")
public record SystemStatusResponse(
    @Schema(description = "SSE 연결 수", example = "15") int sseConnectionCount,
    @Schema(description = "AI 서비스 상태", example = "true") boolean aiServiceHealthy,
    @Schema(description = "AI 서비스 응답 시간 (ms)", example = "120") long aiServiceResponseMs,
    @Schema(description = "Redis 상태", example = "true") boolean redisHealthy,
    @Schema(description = "전체 회원 수", example = "350") long totalMembers,
    @Schema(description = "전체 문장 수", example = "200") long totalSentences,
    @Schema(description = "미사용 문장 수", example = "85") long unusedSentences) {}
