package com.gakkaweo.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank
        @Size(max = 20)
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$", message = "영문으로 시작하며, 영문/숫자/밑줄만 사용 가능합니다")
        String username,
    @NotBlank @Size(min = 8, max = 72) String password) {}
