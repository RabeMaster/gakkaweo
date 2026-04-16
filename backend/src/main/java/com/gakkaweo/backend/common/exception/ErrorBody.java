package com.gakkaweo.backend.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "에러 응답")
public record ErrorBody(
    @Schema(description = "HTTP 상태 코드", example = "400") int status,
    @Schema(description = "에러 코드", example = "VALIDATION_FAILED") String code,
    @Schema(description = "에러 메시지", example = "요청 검증에 실패했습니다") String message,
    @Schema(description = "에러 발생 시각 (ISO 8601)", example = "2026-04-17T12:00:00Z")
        String timestamp) {}
