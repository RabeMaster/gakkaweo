package com.gakkaweo.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "닉네임 강제 변경 요청")
public record ForceNicknameRequest(
    @Schema(description = "변경할 닉네임", example = "새닉네임")
        @NotBlank
        @Size(max = 12)
        @Pattern(regexp = "^[가-힣a-zA-Z0-9_ ]+$", message = "한글, 영문, 숫자, 밑줄, 공백만 사용할 수 있습니다")
        String nickname) {}
