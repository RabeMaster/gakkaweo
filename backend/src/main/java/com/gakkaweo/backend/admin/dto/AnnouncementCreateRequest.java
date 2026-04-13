package com.gakkaweo.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record AnnouncementCreateRequest(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 5000) String content,
    @NotBlank @Pattern(regexp = "INFO|MAINTENANCE|WARNING") String type,
    @NotNull Instant startsAt,
    Instant endsAt) {}
