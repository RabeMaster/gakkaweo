package com.gakkaweo.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

@Schema(description = "공지 수정 요청 (부분 업데이트)")
public record AnnouncementUpdateRequest(
    @Schema(description = "제목", nullable = true, example = "수정된 제목") @Size(max = 200) String title,
    @Schema(description = "내용", nullable = true, example = "수정된 내용") @Size(max = 5000)
        String content,
    @Schema(
            description = "공지 유형",
            nullable = true,
            allowableValues = {"INFO", "MAINTENANCE", "WARNING"},
            example = "INFO")
        @Pattern(regexp = "INFO|MAINTENANCE|WARNING")
        String type,
    @Schema(description = "활성 여부", nullable = true, example = "false") Boolean active,
    @Schema(description = "시작 시각", nullable = true, example = "2026-04-17T00:00:00Z")
        Instant startsAt,
    @Schema(description = "종료 시각", nullable = true, example = "2026-04-18T00:00:00Z")
        Instant endsAt) {}
