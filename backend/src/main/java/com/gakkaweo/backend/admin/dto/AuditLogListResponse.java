package com.gakkaweo.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "감사 로그 목록 응답")
public record AuditLogListResponse(
    @Schema(description = "감사 로그 목록") List<AuditLogResponse> content,
    @Schema(description = "현재 페이지", example = "0") int page,
    @Schema(description = "페이지 크기", example = "20") int size,
    @Schema(description = "전체 건수", example = "500") long totalElements,
    @Schema(description = "전체 페이지 수", example = "25") int totalPages) {}
