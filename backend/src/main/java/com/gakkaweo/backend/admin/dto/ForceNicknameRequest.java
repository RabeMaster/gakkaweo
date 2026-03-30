package com.gakkaweo.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ForceNicknameRequest(
    @NotBlank
        @Size(max = 12)
        @Pattern(regexp = "^[가-힣a-zA-Z0-9_ ]+$", message = "한글, 영문, 숫자, 밑줄, 공백만 사용할 수 있습니다")
        String nickname) {}
