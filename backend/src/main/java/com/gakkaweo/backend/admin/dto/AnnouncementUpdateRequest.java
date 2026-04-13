package com.gakkaweo.backend.admin.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record AnnouncementUpdateRequest(
    @Size(max = 200) String title,
    @Size(max = 5000) String content,
    @Pattern(regexp = "INFO|MAINTENANCE|WARNING") String type,
    Boolean active,
    Instant startsAt,
    Instant endsAt) {}
