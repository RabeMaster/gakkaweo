package com.gakkaweo.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DuplicateCheckRequest(@NotBlank @Size(max = 500) String sentence) {}
