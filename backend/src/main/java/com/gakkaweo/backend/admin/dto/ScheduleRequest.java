package com.gakkaweo.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "스케줄 지정 요청")
public record ScheduleRequest(
    @Schema(description = "출제 예정일", example = "2026-04-20") @NotNull LocalDate date) {}
