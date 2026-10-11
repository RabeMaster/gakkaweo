package com.gakkaweo.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "로컬 계정 비밀번호 변경")
public record PasswordChangeRequest(
    @Schema(description = "현재 비밀번호") @NotBlank @Size(max = 72) String currentPassword,
    @Schema(description = "새 비밀번호 (8자 이상)") @NotBlank @Size(min = 8, max = 72)
        String newPassword) {}
