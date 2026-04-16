package com.gakkaweo.backend.ranking.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "하트비트 응답 (SSE)")
public record HeartbeatResponse(
    @Schema(description = "현재 SSE 연결 수", example = "15") int sseConnectionCount) {}
