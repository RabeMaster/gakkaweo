package com.gakkaweo.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "CSV 업로드 결과")
public record CsvUploadResponse(
    @Schema(description = "전체 행 수", example = "100") int totalRows,
    @Schema(description = "등록 성공 수", example = "95") int successCount,
    @Schema(description = "중복 건수", example = "5") int duplicateCount) {}
