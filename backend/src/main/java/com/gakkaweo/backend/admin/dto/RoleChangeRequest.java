package com.gakkaweo.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record RoleChangeRequest(@NotBlank String role) {}
