package com.gakkaweo.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "긴급 교체 요청")
public record EmergencyReplaceRequest(
    @Schema(description = "교체할 문장 공개 ID", example = "550e8400-e29b-41d4-a716-446655440000") @NotNull
        UUID newSentencePublicId,
    @Schema(description = "기존 문장 풀 복귀 여부", example = "true") boolean returnOldToPool) {}
