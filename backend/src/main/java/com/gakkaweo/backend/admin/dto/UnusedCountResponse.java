package com.gakkaweo.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "미사용 문장 수 응답")
public record UnusedCountResponse(@Schema(description = "미사용 문장 수", example = "85") long count) {}
