package com.gakkaweo.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NicknameUpdateRequest(
    @NotBlank
        @Size(max = 12)
        @Pattern(regexp = "^[가-힣a-zA-Z0-9_ ]+$", message = "한글, 영문, 숫자, 밑줄, 공백만 사용 가능합니다")
        String nickname) {}
