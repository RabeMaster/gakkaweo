package com.gakkaweo.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "역할 변경 요청")
public record RoleChangeRequest(
    // SUPERADMIN은 의도적으로 제외 — DB 마이그레이션 또는 운영 SQL로만 부여 가능
    @Schema(
            description = "변경할 역할 (SUPERADMIN은 API로 부여 불가)",
            allowableValues = {"USER", "ADMIN"},
            example = "ADMIN")
        @NotBlank
        @Pattern(regexp = "USER|ADMIN")
        String role) {}
