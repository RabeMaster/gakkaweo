package com.gakkaweo.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

@Schema(description = "공지 등록 요청")
public record AnnouncementCreateRequest(
    @Schema(description = "제목 (최대 200자)", example = "서버 점검 안내") @NotBlank @Size(max = 200)
        String title,
    @Schema(description = "내용 (최대 5000자)", nullable = true, example = "4월 20일 점검 예정입니다")
        @Size(max = 5000)
        String content,
    @Schema(
            description = "공지 유형",
            allowableValues = {"INFO", "MAINTENANCE", "WARNING"},
            example = "MAINTENANCE")
        @NotBlank
        @Pattern(regexp = "INFO|MAINTENANCE|WARNING")
        String type,
    @Schema(description = "시작 시각", example = "2026-04-17T00:00:00Z") @NotNull Instant startsAt,
    @Schema(description = "종료 시각", nullable = true, example = "2026-04-18T00:00:00Z")
        Instant endsAt) {}
