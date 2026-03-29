package com.gakkaweo.backend.admin.dto;

import jakarta.validation.constraints.Size;
import java.time.Instant;

public record AnnouncementUpdateRequest(
    @Size(max = 200) String title,
    String content,
    String type,
    Boolean active,
    Instant startsAt,
    Instant endsAt) {}
