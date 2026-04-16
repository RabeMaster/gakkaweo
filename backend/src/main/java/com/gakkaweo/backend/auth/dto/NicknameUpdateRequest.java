package com.gakkaweo.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "닉네임 변경 요청")
public record NicknameUpdateRequest(
    @Schema(description = "변경할 닉네임 (1~12자)", example = "새닉네임")
        @NotBlank
        @Size(max = 12)
        @Pattern(regexp = "^[가-힣a-zA-Z0-9_ ]+$", message = "한글, 영문, 숫자, 밑줄, 공백만 사용 가능합니다")
        String nickname) {}
