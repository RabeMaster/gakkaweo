package com.gakkaweo.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record DuplicateCheckRequest(@NotBlank String sentence) {}
