package com.gakkaweo.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SentenceCreateRequest(@NotBlank @Size(max = 500) String sentence) {}
