package com.gakkaweo.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RoleChangeRequest(@NotBlank @Pattern(regexp = "USER|ADMIN") String role) {}
