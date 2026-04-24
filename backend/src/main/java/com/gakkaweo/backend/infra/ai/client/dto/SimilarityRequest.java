package com.gakkaweo.backend.infra.ai.client.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SimilarityRequest(
    @Schema(description = "기준 문장", example = "오늘도 좋은 하루 보내세요") String sentence,
    @Schema(description = "비교할 추측 입력", example = "좋은 하루 되세요") String guess) {}
