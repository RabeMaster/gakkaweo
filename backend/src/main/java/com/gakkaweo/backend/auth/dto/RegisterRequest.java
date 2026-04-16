package com.gakkaweo.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "회원가입 요청")
public record RegisterRequest(
    @Schema(description = "아이디 (4~20자, 영문/숫자)", example = "testuser")
        @NotBlank
        @Size(max = 20)
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$", message = "영문으로 시작하며, 영문/숫자/밑줄만 사용 가능합니다")
        String username,
    @Schema(description = "비밀번호 (8~72자)", example = "password123") @NotBlank @Size(max = 72)
        String password) {}
